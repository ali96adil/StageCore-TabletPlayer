package com.stagecore.player.model;

public final class MediaItemRef {
    public final String key;
    public final String type;
    public final String file;
    public final String url;

    public MediaItemRef(String key, String type, String file, String url) {
        this.key = key;
        this.type = type;
        this.file = file;
        this.url = url;
    }
}
