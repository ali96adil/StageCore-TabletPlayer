package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;
import com.stagecore.player.model.TabletManifest;

import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local boundary between the transport and the existing playback engine.
 * Transport never owns playback state; it only forwards validated commands.
 */
public final class StageCoreRuntimeBridge {
    public interface SettingsExecutor {
        CommandResult setBrightnessPercent(int percent);
        CommandResult setShowMode(boolean enabled);
        CommandResult setVideoScaleMode(String mode);
        CommandResult setOrientationMode(String mode);
        CommandResult setLiveRotationDegrees(int degrees);
        JSONObject observedHealth();
    }

    private static final AtomicReference<ManifestExecutor> EXECUTOR = new AtomicReference<>();
    private static final AtomicReference<SettingsExecutor> SETTINGS_EXECUTOR = new AtomicReference<>();

    private StageCoreRuntimeBridge() {}

    public static void register(ManifestExecutor executor) {
        EXECUTOR.set(executor);
    }

    public static void registerSettings(SettingsExecutor executor) {
        SETTINGS_EXECUTOR.set(executor);
    }

    public static void unregisterSettings(SettingsExecutor executor) {
        SETTINGS_EXECUTOR.compareAndSet(executor, null);
    }

    public static boolean isReady() {
        return EXECUTOR.get() != null;
    }

    public static String projectId() {
        TabletManifest manifest = manifest();
        return manifest == null ? "" : safe(manifest.stageCoreProjectId);
    }

    public static CommandResult validateScope(String projectId, String snapshotId, String manifestId) {
        ManifestExecutor executor = EXECUTOR.get();
        if (executor == null) return CommandResult.failed("PLAYER_NOT_READY", "Tablet player is not ready");
        return executor.validateScope(projectId, snapshotId, manifestId);
    }

    public static CommandResult validateV2ManifestHint(String manifestId) {
        ManifestExecutor executor = EXECUTOR.get();
        if (executor == null) return CommandResult.failed("PLAYER_NOT_READY", "Tablet player is not ready");
        return executor.validateV2ManifestHint(manifestId);
    }

    /**
     * v2 observation mirrors the Hub-owned active scope while retaining only
     * local content identity from tablet_manifest.json. Legacy manifest
     * Project/Snapshot fields must never self-assign a v2 tablet.
     */
    public static JSONObject assignedObservedState(String projectId, String snapshotId) {
        TabletManifest manifest = manifest();
        ManifestExecutor executor = EXECUTOR.get();
        String manifestId = manifest == null ? "" : safe(manifest.tabletManifestId);
        Map<String, Object> playerState = executor == null
                ? Collections.emptyMap()
                : executor.playerObservedStateValues();
        JSONObject object = new JSONObject(composeAssignedObservedStateValues(
                safe(projectId),
                safe(snapshotId),
                manifestId,
                playerState));
        try {
            SettingsExecutor settings = SETTINGS_EXECUTOR.get();
            if (settings != null) {
                JSONObject health = settings.observedHealth();
                if (health != null) object.put("health", health);
            }
        } catch (Exception ignored) {}
        return object;
    }

    static Map<String, Object> composeAssignedObservedStateValues(
            String projectId,
            String snapshotId,
            String manifestId,
            Map<String, Object> playerState) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("project_id", safe(projectId));
        values.put("runtime_snapshot_id", safe(snapshotId));
        if (!safe(manifestId).isEmpty()) {
            values.put("tablet_manifest_id", safe(manifestId));
        }
        values.put("player_state",
                playerState == null
                        ? Collections.emptyMap()
                        : new LinkedHashMap<>(playerState));
        return values;
    }

    public static JSONObject observedState() {
        TabletManifest manifest = manifest();
        JSONObject object = new JSONObject();
        try {
            if (manifest != null) {
                object.put("project_id", safe(manifest.stageCoreProjectId));
                object.put("runtime_snapshot_id", safe(manifest.runtimeSnapshotId));
                object.put("tablet_manifest_id", safe(manifest.tabletManifestId));
            }
            SettingsExecutor settings = SETTINGS_EXECUTOR.get();
            if (settings != null) {
                JSONObject health = settings.observedHealth();
                if (health != null) object.put("health", health);
            }
        } catch (Exception ignored) {}
        return object;
    }

    /**
     * Device-level observation used before a Hub-owned Project assignment exists.
     * It deliberately excludes Project and Runtime Snapshot authority so a stale
     * local manifest cannot self-assign this tablet to a show.
     */
    public static JSONObject inventoryObservedState() {
        TabletManifest manifest = manifest();
        JSONObject object = new JSONObject();
        try {
            object.put("player_ready", isReady());
            object.put("local_manifest_present", manifest != null);
            if (manifest != null) {
                object.put("local_tablet_manifest_id", safe(manifest.tabletManifestId));
            }
        } catch (Exception ignored) {}
        return object;
    }

    public static CommandResult enterAssignmentSafeState() {
        ManifestExecutor executor = EXECUTOR.get();
        if (executor == null) {
            return CommandResult.failed("PLAYER_NOT_READY", "Tablet player is not ready for assignment");
        }
        CommandResult result = executor.stopMain();
        if (result.status != CommandStatus.COMPLETED) {
            return CommandResult.failed("SAFE_MEDIA_STOP_FAILED", result.message);
        }
        result = executor.hideOverlay(0);
        if (result.status != CommandStatus.COMPLETED) {
            return CommandResult.failed("SAFE_MEDIA_OVERLAY_FAILED", result.message);
        }
        result = executor.hideLive();
        if (result.status != CommandStatus.COMPLETED) {
            return CommandResult.failed("SAFE_MEDIA_LIVE_FAILED", result.message);
        }
        result = executor.blackout();
        if (result.status != CommandStatus.COMPLETED) {
            return CommandResult.failed("SAFE_MEDIA_BLACKOUT_FAILED", result.message);
        }
        return CommandResult.completed("Tablet entered assignment safe-media state");
    }

    public static CommandResult execute(String commandType, JSONObject payload) {
        ManifestExecutor executor = EXECUTOR.get();
        if (executor == null) return CommandResult.failed("PLAYER_NOT_READY", "Tablet player is not ready");
        if (payload == null) payload = new JSONObject();
        switch (commandType) {
            case "TABLET_PREPARE":
                if (!hasExactlyOneMediaSelector(
                        payload.has("tablet_cue_id"),
                        payload.has("tablet_sequence"),
                        payload.has("media_number"))) {
                    return CommandResult.rejected(
                            "MEDIA_SELECTOR_AMBIGUOUS",
                            "TABLET_PREPARE requires exactly one of tablet_cue_id, tablet_sequence, or media_number");
                }
                if (payload.has("tablet_cue_id")) {
                    String cueId = payload.optString("tablet_cue_id", "").trim();
                    return cueId.isEmpty()
                            ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "tablet_cue_id must not be blank")
                            : executor.prepareCueById(cueId);
                }
                if (payload.has("tablet_sequence")) {
                    int sequence = payload.optInt("tablet_sequence", -1);
                    return sequence < 1
                            ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "tablet_sequence must be positive")
                            : executor.prepareCue(sequence);
                }
                if (payload.has("media_number")) {
                    int mediaNumber = payload.optInt("media_number", -1);
                    return mediaNumber < 1
                            ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "media_number must be positive")
                            : executor.prepareMain(mediaNumber);
                }
                return CommandResult.rejected(
                        "MEDIA_SELECTOR_REQUIRED",
                        "TABLET_PREPARE requires tablet_cue_id, tablet_sequence, or media_number");
            case "TABLET_PLAY":
                if (!hasExactlyOneMediaSelector(
                        payload.has("tablet_cue_id"),
                        payload.has("tablet_sequence"),
                        payload.has("media_number"))) {
                    return CommandResult.rejected(
                            "MEDIA_SELECTOR_AMBIGUOUS",
                            "TABLET_PLAY requires exactly one of tablet_cue_id, tablet_sequence, or media_number");
                }
                if (payload.has("tablet_cue_id")) {
                    String cueId = payload.optString("tablet_cue_id", "").trim();
                    return cueId.isEmpty()
                            ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "tablet_cue_id must not be blank")
                            : executor.goCueById(cueId);
                }
                if (payload.has("tablet_sequence")) {
                    int sequence = payload.optInt("tablet_sequence", -1);
                    return sequence < 1
                            ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "tablet_sequence must be positive")
                            : executor.goCue(sequence);
                }
                if (payload.has("media_number")) {
                    int mediaNumber = payload.optInt("media_number", -1);
                    return mediaNumber < 1
                            ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "media_number must be positive")
                            : executor.playMain(mediaNumber);
                }
                return CommandResult.rejected(
                        "MEDIA_SELECTOR_REQUIRED",
                        "TABLET_PLAY requires tablet_cue_id, tablet_sequence, or media_number");
            case "TABLET_PAUSE": return executor.pauseMain();
            case "TABLET_STOP": return executor.stopMain();
            case "TABLET_BLACKOUT": return executor.blackout();
            case "TABLET_BLACKOUT_CLEAR": return executor.clearBlackout();
            case "TABLET_OVERLAY_PLAY":
                if (!payload.has("media_number")) {
                    return CommandResult.rejected(
                            "MEDIA_SELECTOR_REQUIRED",
                            "TABLET_OVERLAY_PLAY requires media_number");
                }
                int overlayMediaNumber = payload.optInt("media_number", -1);
                return overlayMediaNumber < 1
                        ? CommandResult.rejected("MEDIA_SELECTOR_INVALID", "media_number must be positive")
                        : executor.playOverlay(overlayMediaNumber);
            case "TABLET_OVERLAY_CLEAR": return executor.hideOverlay(payload.optLong("dissolve_ms", 0));
            case "TABLET_LIVE_SHOW":
                String mediaKey = payload.optString("media_key", "").trim();
                String directUrl = payload.optString("url", "").trim();
                if ((mediaKey.isEmpty()) == (directUrl.isEmpty())) {
                    return CommandResult.rejected("LIVE_SOURCE_INVALID", "Provide exactly one of media_key or url");
                }
                if (!directUrl.isEmpty()) {
                    if (!isAllowedDirectLiveUrl(directUrl)) {
                        return CommandResult.rejected("LIVE_URL_INVALID", "Live URL must be absolute HTTP(S) without credentials");
                    }
                    return executor.showLiveUrl(directUrl);
                }
                return executor.showLive(mediaKey);
            case "TABLET_LIVE_HIDE": return executor.hideLive();
            case "TABLET_BRIGHTNESS_SET": {
                SettingsExecutor settings = SETTINGS_EXECUTOR.get();
                if (settings == null) return CommandResult.failed("SETTINGS_NOT_READY", "Tablet settings controller is not ready");
                if (!payload.has("brightness_percent")) return CommandResult.rejected("INVALID_BRIGHTNESS", "brightness_percent is required");
                int percent = payload.optInt("brightness_percent", -1);
                if (percent < 5 || percent > 100) return CommandResult.rejected("INVALID_BRIGHTNESS", "brightness_percent must be 5..100");
                return settings.setBrightnessPercent(percent);
            }
            case "TABLET_SHOW_MODE_SET": {
                SettingsExecutor settings = SETTINGS_EXECUTOR.get();
                if (settings == null) return CommandResult.failed("SETTINGS_NOT_READY", "Tablet settings controller is not ready");
                if (!payload.has("show_mode") || !(payload.opt("show_mode") instanceof Boolean)) {
                    return CommandResult.rejected("INVALID_SHOW_MODE", "show_mode boolean is required");
                }
                return settings.setShowMode(payload.optBoolean("show_mode"));
            }
            case "TABLET_VIDEO_SCALE_SET": {
                SettingsExecutor settings = SETTINGS_EXECUTOR.get();
                if (settings == null) return CommandResult.failed("SETTINGS_NOT_READY", "Tablet settings controller is not ready");
                String mode = payload.optString("video_scale_mode", "").trim();
                if (!AppSettings.SCALE_FULL.equals(mode) && !AppSettings.SCALE_FIT.equals(mode) && !AppSettings.SCALE_CROP.equals(mode)) {
                    return CommandResult.rejected("INVALID_VIDEO_SCALE", "video_scale_mode must be FULL, FIT, or CROP");
                }
                return settings.setVideoScaleMode(mode);
            }
            case "TABLET_ORIENTATION_SET": {
                SettingsExecutor settings = SETTINGS_EXECUTOR.get();
                if (settings == null) return CommandResult.failed("SETTINGS_NOT_READY", "Tablet settings controller is not ready");
                String mode = payload.optString("orientation_mode", "").trim();
                if (!AppSettings.ORIENTATION_AUTO.equals(mode) && !AppSettings.ORIENTATION_LANDSCAPE.equals(mode) && !AppSettings.ORIENTATION_PORTRAIT.equals(mode)) {
                    return CommandResult.rejected("INVALID_ORIENTATION", "orientation_mode must be AUTO, LANDSCAPE, or PORTRAIT");
                }
                return settings.setOrientationMode(mode);
            }
            case "TABLET_LIVE_ROTATION_SET": {
                SettingsExecutor settings = SETTINGS_EXECUTOR.get();
                if (settings == null) return CommandResult.failed("SETTINGS_NOT_READY", "Tablet settings controller is not ready");
                if (!payload.has("live_rotation_degrees")) {
                    return CommandResult.rejected("INVALID_LIVE_ROTATION", "live_rotation_degrees is required");
                }
                int degrees = payload.optInt("live_rotation_degrees", -1);
                if (degrees != 0 && degrees != 90 && degrees != 180 && degrees != 270) {
                    return CommandResult.rejected("INVALID_LIVE_ROTATION", "live_rotation_degrees must be 0, 90, 180, or 270");
                }
                return settings.setLiveRotationDegrees(degrees);
            }
            default: return CommandResult.rejected("UNSUPPORTED_COMMAND", "Unsupported StageCore command " + commandType);
        }
    }

    public static CommandResult executeLiveAsync(JSONObject payload, MjpegLiveView.Listener listener) {
        ManifestExecutor executor = EXECUTOR.get();
        if (executor == null) {
            return CommandResult.failed("PLAYER_NOT_READY", "Tablet player is not ready");
        }
        if (payload == null) payload = new JSONObject();
        String mediaKey = payload.optString("media_key", "").trim();
        String directUrl = payload.optString("url", "").trim();
        if ((mediaKey.isEmpty()) == (directUrl.isEmpty())) {
            return CommandResult.rejected("LIVE_SOURCE_INVALID", "Provide exactly one of media_key or url");
        }
        if (!directUrl.isEmpty()) {
            if (!isAllowedDirectLiveUrl(directUrl)) {
                return CommandResult.rejected("LIVE_URL_INVALID", "Live URL must be absolute HTTP(S) without credentials");
            }
            return executor.showLiveUrlAsync(directUrl, listener);
        }
        return executor.showLiveAsync(mediaKey, listener);
    }

    static boolean hasExactlyOneMediaSelector(
            boolean hasCueId,
            boolean hasSequence,
            boolean hasMediaNumber) {
        int count = 0;
        if (hasCueId) count++;
        if (hasSequence) count++;
        if (hasMediaNumber) count++;
        return count == 1;
    }

    static boolean isAllowedDirectLiveUrl(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) return false;
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty()
                    && uri.getUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static TabletManifest manifest() {
        ManifestExecutor executor = EXECUTOR.get();
        return executor == null ? null : executor.activeManifest();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
