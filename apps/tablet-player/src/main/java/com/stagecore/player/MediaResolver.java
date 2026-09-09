package com.stagecore.player;

import android.os.Environment;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletManifest;

import java.io.File;

public final class MediaResolver {
    private final File baseDir;

    public MediaResolver() {
        baseDir = new File(Environment.getExternalStorageDirectory(), "TheatreVideos");
    }

    public File baseDir() {
        return baseDir;
    }

    public File resolveFile(TabletManifest manifest, String mediaKey) {
        MediaItemRef item = manifest.media.get(mediaKey);
        if (item == null || item.file == null) return null;
        return new File(baseDir, item.file);
    }

    public String resolveLiveUrl(TabletManifest manifest, String mediaKey) {
        MediaItemRef item = manifest.media.get(mediaKey);
        if (item == null) return null;
        return item.url;
    }
}
