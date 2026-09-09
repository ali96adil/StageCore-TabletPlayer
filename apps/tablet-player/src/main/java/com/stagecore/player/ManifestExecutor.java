package com.stagecore.player;

import com.stagecore.player.model.CommandResult;
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

    public CommandResult prepareCue(int tabletSequence) {
        TabletCue cue = activeManifest().cueByLocalSequence(tabletSequence);
        if (cue == null) return CommandResult.failed("CUE_NOT_FOUND", "No local tablet cue sequence " + tabletSequence);
        for (TabletAction action : cue.actions) {
            if ("main.play".equals(action.type) || "main.prepare".equals(action.type)) {
                File file = mediaResolver.resolveFile(activeManifest(), action.mediaKey);
                CommandResult result = player.prepareMain(file);
                if (result.status != com.stagecore.player.model.CommandStatus.COMPLETED) return result;
            }
        }
        return CommandResult.completed("Prepared cue " + cue.tabletCueId);
    }

    public CommandResult goCue(int tabletSequence) {
        TabletCue cue = activeManifest().cueByLocalSequence(tabletSequence);
        if (cue == null) return CommandResult.failed("CUE_NOT_FOUND", "No local tablet cue sequence " + tabletSequence);
        CommandResult last = CommandResult.completed("No-op cue " + cue.tabletCueId);
        for (TabletAction action : cue.actions) {
            last = executeAction(action);
            if (last.status != com.stagecore.player.model.CommandStatus.COMPLETED) return last;
        }
        return last;
    }

    public CommandResult playMain(int number) {
        String key = String.format(java.util.Locale.US, "main.%02d", number);
        File file = mediaResolver.resolveFile(activeManifest(), key);
        return player.playMain(file);
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

    private CommandResult executeAction(TabletAction action) {
        MediaItemRef media = activeManifest().media.get(action.mediaKey);
        if (media == null) return CommandResult.failed("MEDIA_KEY_NOT_FOUND", "No media key " + action.mediaKey);

        switch (action.type) {
            case "main.prepare":
                return player.prepareMain(mediaResolver.resolveFile(activeManifest(), action.mediaKey));
            case "main.play":
                return player.playMain(mediaResolver.resolveFile(activeManifest(), action.mediaKey));
            case "overlay.play":
                return player.playOverlay(mediaResolver.resolveFile(activeManifest(), action.mediaKey), action.dissolveInMs, action.dissolveOutMs);
            case "overlay.hide":
                return player.hideOverlay(action.dissolveOutMs);
            case "live.show":
                return player.showLive(mediaResolver.resolveLiveUrl(activeManifest(), action.mediaKey));
            case "live.hide":
                return player.hideLive();
            case "blackout":
                return player.blackout();
            default:
                return CommandResult.rejected("UNSUPPORTED_ACTION", "Unsupported action type " + action.type);
        }
    }
}
