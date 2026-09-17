package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
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
