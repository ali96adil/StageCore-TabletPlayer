package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;
import com.stagecore.player.model.TabletManifest;

import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Process-local boundary between the transport and the existing playback engine.
 * Transport never owns playback state; it only forwards validated commands.
 */
public final class StageCoreRuntimeBridge {
    private static final AtomicReference<ManifestExecutor> EXECUTOR = new AtomicReference<>();

    private StageCoreRuntimeBridge() {}

    public static void register(ManifestExecutor executor) {
        EXECUTOR.set(executor);
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
        JSONObject object = new JSONObject();
        try {
            object.put("project_id", safe(projectId));
            object.put("runtime_snapshot_id", safe(snapshotId));
            if (manifest != null) {
                object.put("tablet_manifest_id", safe(manifest.tabletManifestId));
            }
        } catch (Exception ignored) {}
        return object;
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
                if (payload.has("tablet_cue_id")) return executor.prepareCueById(payload.optString("tablet_cue_id"));
                if (payload.has("tablet_sequence")) return executor.prepareCue(payload.optInt("tablet_sequence", -1));
                return executor.prepareMain(payload.optInt("media_number", 1));
            case "TABLET_PLAY":
                if (payload.has("tablet_cue_id")) return executor.goCueById(payload.optString("tablet_cue_id"));
                if (payload.has("tablet_sequence")) return executor.goCue(payload.optInt("tablet_sequence", -1));
                return executor.playMain(payload.optInt("media_number", 1));
            case "TABLET_PAUSE": return executor.pauseMain();
            case "TABLET_STOP": return executor.stopMain();
            case "TABLET_BLACKOUT": return executor.blackout();
            case "TABLET_BLACKOUT_CLEAR": return executor.clearBlackout();
            case "TABLET_OVERLAY_PLAY": return executor.playOverlay(payload.optInt("media_number", 1));
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
