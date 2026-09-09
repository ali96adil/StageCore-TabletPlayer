package com.stagecore.player;

import android.os.Environment;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletManifest;

import java.io.File;

public final class MediaResolver {
    public static final String MEDIA_FOLDER_NAME = "TheatreVideos";
    public static final String MANIFEST_FILE_NAME = "tablet_manifest.json";

    private final File baseDir;

    public MediaResolver() {
        baseDir = new File(Environment.getExternalStorageDirectory(), MEDIA_FOLDER_NAME);
    }

    public File baseDir() {
        return baseDir;
    }

    public File manifestFile() {
        return new File(baseDir, MANIFEST_FILE_NAME);
    }

    public boolean ensureBaseDir() {
        return baseDir.exists() || baseDir.mkdirs();
    }

    public File resolveFile(TabletManifest manifest, String mediaKey) {
        if (manifest == null || mediaKey == null) return null;
        MediaItemRef item = manifest.media.get(mediaKey);
        if (item == null || item.file == null) return null;
        return new File(baseDir, item.file);
    }

    public String resolveLiveUrl(TabletManifest manifest, String mediaKey) {
        if (manifest == null || mediaKey == null) return null;
        MediaItemRef item = manifest.media.get(mediaKey);
        if (item == null) return null;
        return item.url;
    }

    public boolean isReadableMedia(TabletManifest manifest, String mediaKey) {
        File file = resolveFile(manifest, mediaKey);
        return file != null && file.exists() && file.isFile() && file.canRead();
    }

    public String mediaFolderHelp() {
        return "Media folder: " + baseDir.getAbsolutePath()
                + "\nManifest file: " + manifestFile().getAbsolutePath()
                + "\nMain files: main_01.mp4 ... main_06.mp4"
                + "\nOverlay files: overlay_01.mp4 ... overlay_99.mp4";
    }
}
