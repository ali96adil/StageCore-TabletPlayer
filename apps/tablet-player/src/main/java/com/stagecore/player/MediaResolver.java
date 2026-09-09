package com.stagecore.player;

import android.os.Environment;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import java.io.File;
import java.util.Map;

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

    public String prepareFolderSummary() {
        boolean ok = ensureBaseDir();
        return (ok ? "تم تجهيز مجلد الفيديوات" : "تعذر إنشاء مجلد الفيديوات")
                + "\n" + mediaFolderHelpArabic();
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

    public String mediaFolderHelpArabic() {
        return "مجلد الفيديوات: " + baseDir.getAbsolutePath()
                + "\nملف المنفست: " + manifestFile().getAbsolutePath()
                + "\nفيديوات الخلفية: main_01.mp4 ... main_06.mp4"
                + "\nفيديوات الطبقة: overlay_01.mp4 ... overlay_99.mp4";
    }

    public String scanSummary(TabletManifest manifest) {
        ensureBaseDir();
        int mainCount = countExisting("main_", 1, 6);
        int overlayCount = countExisting("overlay_", 1, 99);
        int missingRefs = 0;
        int checkedRefs = 0;
        StringBuilder missing = new StringBuilder();

        if (manifest != null) {
            for (Map.Entry<String, MediaItemRef> entry : manifest.media.entrySet()) {
                MediaItemRef item = entry.getValue();
                if (item == null || item.file == null) continue;
                checkedRefs++;
                File file = new File(baseDir, item.file);
                if (!file.exists() || !file.isFile() || !file.canRead()) {
                    missingRefs++;
                    missing.append("\n- ").append(entry.getKey()).append(" → ").append(item.file);
                }
            }
            for (TabletCue cue : manifest.cues) {
                for (TabletAction action : cue.actions) {
                    if (action.mediaKey == null || action.mediaKey.trim().isEmpty()) continue;
                    MediaItemRef item = manifest.media.get(action.mediaKey);
                    if (item == null) {
                        missingRefs++;
                        missing.append("\n- media_key غير موجود: ").append(action.mediaKey);
                    }
                }
            }
        }

        String manifestStatus = manifestFile().exists() ? "موجود" : "غير موجود، راح يستخدم sample داخلي";
        String result = "فحص ملفات الفيديو"
                + "\nmain موجودة: " + mainCount + "/6"
                + "\noverlay موجودة: " + overlayCount + "/99"
                + "\nmanifest: " + manifestStatus
                + "\nمراجع manifest المفحوصة: " + checkedRefs
                + "\nالنواقص: " + missingRefs;
        if (missing.length() > 0) result += missing.toString();
        return result;
    }

    private int countExisting(String prefix, int from, int to) {
        int count = 0;
        for (int i = from; i <= to; i++) {
            File file = new File(baseDir, String.format(java.util.Locale.US, "%s%02d.mp4", prefix, i));
            if (file.exists() && file.isFile() && file.canRead()) count++;
        }
        return count;
    }
}
