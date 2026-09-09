package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.CommandStatus;
import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import java.io.File;

public final class ManifestExecutor {
    private final ManifestStore manifestStore;
    private final MediaResolver mediaResolver;
    private final TabletPlayer player;

    public ManifestExecutor(ManifestStore manifestStore, MediaResolver mediaResolver, TabletPlayer player) {
        this.manifestStore = manifestStore;
        this.mediaResolver = mediaResolver;
        this.player = player;
    }

    public TabletManifest activeManifest() {
        return manifestStore.activeManifest();
    }

    public CommandResult validateScope(String projectId, String snapshotId, String manifestId) {
        TabletManifest manifest = activeManifest();
        if (!matches(projectId, manifest.stageCoreProjectId)) {
            return CommandResult.rejected("PROJECT_MISMATCH", "Command project does not match active tablet manifest");
        }
        if (!matches(snapshotId, manifest.runtimeSnapshotId)) {
            return CommandResult.rejected("SNAPSHOT_MISMATCH", "Command snapshot does not match active tablet manifest");
        }
        if (!matches(manifestId, manifest.tabletManifestId)) {
            return CommandResult.rejected("MANIFEST_MISMATCH", "Command tablet manifest does not match active tablet manifest");
        }
        return CommandResult.completed("Scope accepted");
    }

    public CommandResult prepareCue(int tabletSequence) {
        TabletCue cue = activeManifest().cueByLocalSequence(tabletSequence);
        if (cue == null) return CommandResult.failed("CUE_NOT_FOUND", "No local tablet cue sequence " + tabletSequence);
        return prepareCue(cue);
    }

    public CommandResult prepareCueById(String tabletCueId) {
        TabletCue cue = activeManifest().cueById(tabletCueId);
        if (cue == null) return CommandResult.failed("CUE_NOT_FOUND", "No local tablet cue id " + tabletCueId);
        return prepareCue(cue);
    }

    public CommandResult goCue(int tabletSequence) {
        TabletCue cue = activeManifest().cueByLocalSequence(tabletSequence);
        if (cue == null) return CommandResult.failed("CUE_NOT_FOUND", "No local tablet cue sequence " + tabletSequence);
        return goCue(cue);
    }

    public CommandResult goCueById(String tabletCueId) {
        TabletCue cue = activeManifest().cueById(tabletCueId);
        if (cue == null) return CommandResult.failed("CUE_NOT_FOUND", "No local tablet cue id " + tabletCueId);
        return goCue(cue);
    }

    public CommandResult playMain(int number) {
        String key = String.format(java.util.Locale.US, "main.%02d", number);
        File file = mediaResolver.resolveFile(activeManifest(), key);
        return player.playMain(file);
    }

    public CommandResult prepareMain(int number) {
        String key = String.format(java.util.Locale.US, "main.%02d", number);
        File file = mediaResolver.resolveFile(activeManifest(), key);
        return player.prepareMain(file);
    }

    public CommandResult playOverlay(int number) {
        String key = String.format(java.util.Locale.US, "overlay.%02d", number);
        File file = mediaResolver.resolveFile(activeManifest(), key);
        return player.playOverlay(file, 1000, 1000);
    }

    public CommandResult showLive(String mediaKey) {
        String url = mediaResolver.resolveLiveUrl(activeManifest(), mediaKey);
        return player.showLive(url);
    }

    private CommandResult prepareCue(TabletCue cue) {
        for (TabletAction action : cue.actions) {
            if ("main.play".equals(action.type) || "main.prepare".equals(action.type)) {
                File file = mediaResolver.resolveFile(activeManifest(), action.mediaKey);
                CommandResult result = player.prepareMain(file);
                if (result.status != CommandStatus.COMPLETED) return result;
            }
            if ("overlay.play".equals(action.type)) {
                File file = mediaResolver.resolveFile(activeManifest(), action.mediaKey);
                if (file == null || !file.exists() || !file.canRead()) {
                    return CommandResult.failed("MEDIA_NOT_FOUND", "Missing overlay media for " + action.mediaKey);
                }
            }
        }
        return CommandResult.completed("Prepared cue " + cue.tabletCueId);
    }

    private CommandResult goCue(TabletCue cue) {
        CommandResult last = CommandResult.completed("No-op cue " + cue.tabletCueId);
        for (TabletAction action : cue.actions) {
            last = executeAction(action);
            if (last.status != CommandStatus.COMPLETED) return last;
        }
        return last;
    }

    private CommandResult executeAction(TabletAction action) {
        switch (action.type) {
            case "main.prepare":
                return player.prepareMain(mediaResolver.resolveFile(activeManifest(), action.mediaKey));
            case "main.play":
                return player.playMain(mediaResolver.resolveFile(activeManifest(), action.mediaKey));
            case "main.pause":
                return player.pauseMain();
            case "main.stop":
                return player.stopMain();
            case "overlay.play":
                if (missingMedia(action)) return CommandResult.failed("MEDIA_KEY_NOT_FOUND", "No media key " + action.mediaKey);
                return player.playOverlay(mediaResolver.resolveFile(activeManifest(), action.mediaKey), action.dissolveInMs, action.dissolveOutMs);
            case "overlay.hide":
                return player.hideOverlay(action.dissolveOutMs);
            case "live.show":
                if (missingMedia(action)) return CommandResult.failed("MEDIA_KEY_NOT_FOUND", "No media key " + action.mediaKey);
                return player.showLive(mediaResolver.resolveLiveUrl(activeManifest(), action.mediaKey));
            case "live.hide":
                return player.hideLive();
            case "blackout":
                return player.blackout();
            case "blackout.clear":
                return player.clearBlackout();
            default:
                return CommandResult.rejected("UNSUPPORTED_ACTION", "Unsupported action type " + action.type);
        }
    }

    private boolean missingMedia(TabletAction action) {
        if (action.mediaKey == null || action.mediaKey.trim().isEmpty()) return true;
        MediaItemRef media = activeManifest().media.get(action.mediaKey);
        return media == null;
    }

    private boolean matches(String incoming, String active) {
        return incoming == null || incoming.trim().isEmpty() || incoming.equals(active);
    }
}
