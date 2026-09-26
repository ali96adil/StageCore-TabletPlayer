package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;
import com.stagecore.player.model.TabletManifest;

import org.json.JSONObject;

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
            case "TABLET_LIVE_SHOW": return executor.showLive(payload.optString("media_key", ""));
            case "TABLET_LIVE_HIDE": return executor.hideLive();
            default: return CommandResult.rejected("UNSUPPORTED_COMMAND", "Unsupported StageCore command " + commandType);
        }
    }

    private static TabletManifest manifest() {
        ManifestExecutor executor = EXECUTOR.get();
        return executor == null ? null : executor.activeManifest();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
