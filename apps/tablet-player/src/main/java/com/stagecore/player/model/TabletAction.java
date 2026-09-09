package com.stagecore.player.model;

public final class TabletAction {
    public final String actionId;
    public final String type;
    public final String mediaKey;
    public final int dissolveInMs;
    public final int dissolveOutMs;

    public TabletAction(String actionId, String type, String mediaKey, int dissolveInMs, int dissolveOutMs) {
        this.actionId = actionId;
        this.type = type;
        this.mediaKey = mediaKey;
        this.dissolveInMs = dissolveInMs;
        this.dissolveOutMs = dissolveOutMs;
    }
}
