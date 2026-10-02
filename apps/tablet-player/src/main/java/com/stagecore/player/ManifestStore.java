package com.stagecore.player;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ManifestStore {
    private TabletManifest activeManifest;
    private String activeSource = "none";

    public TabletManifest loadFromDisk(File manifestFile) throws IOException, JSONException {
        if (manifestFile == null) throw new IOException("Manifest file is null");
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            throw new IOException("Manifest file not found: " + manifestFile.getAbsolutePath());
        }
        String json = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        activeManifest = ManifestJsonCodec.parse(json);
        activeSource = manifestFile.getAbsolutePath();
        return activeManifest;
    }

    public TabletManifest tryLoadFromDiskOrSample(File manifestFile) {
        try {
            return loadFromDisk(manifestFile);
        } catch (Exception ignored) {
            TabletManifest discovered = loadAutoDiscoveredMedia(manifestFile);
            return discovered != null ? discovered : loadBundledSample();
        }
    }

    TabletManifest loadAutoDiscoveredMedia(File manifestFile) {
        if (manifestFile == null) return null;
        File folder = manifestFile.getParentFile();
        if (folder == null || !folder.exists() || !folder.isDirectory()) return null;

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        Map<String, MediaItemRef> media = new LinkedHashMap<>();
        for (File file : files) {
            if (file == null || !file.isFile() || !file.canRead()) continue;
            String name = file.getName();
            String lower = name.toLowerCase(Locale.US);
            addAutoDiscoveredMedia(media, lower, name, "main_", "main");
            addAutoDiscoveredMedia(media, lower, name, "overlay_", "overlay");
        }
        if (media.isEmpty()) return null;

        activeManifest = new TabletManifest(
                "tablet_manifest/1",
                "hub_authoritative_v2",
                "hub_authoritative_v2",
                "tablet_manifest_auto_media_v1",
                "StageCore Auto Media",
                media,
                Collections.emptyList()
        );
        activeSource = "auto-discovered TheatreVideos";
        return activeManifest;
    }

    private static void addAutoDiscoveredMedia(
            Map<String, MediaItemRef> media,
            String lowerName,
            String actualName,
            String filenamePrefix,
            String mediaType) {
        if (!lowerName.startsWith(filenamePrefix) || !lowerName.endsWith(".mp4")) return;
        String numberPart = lowerName.substring(filenamePrefix.length(), lowerName.length() - 4);
        if (numberPart.isEmpty()) return;
        int number;
        try {
            number = Integer.parseInt(numberPart);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (number < 1) return;
        String key = String.format(Locale.US, "%s.%02d", mediaType, number);
        if (!media.containsKey(key)) {
            media.put(key, new MediaItemRef(key, mediaType, actualName, null));
        }
    }

    public TabletManifest loadBundledSample() {
        Map<String, MediaItemRef> media = new LinkedHashMap<>();
        media.put("main.01", new MediaItemRef("main.01", "main", "main_01.mp4", null));
        media.put("overlay.01", new MediaItemRef("overlay.01", "overlay", "overlay_01.mp4", null));
        media.put("live.camera.01", new MediaItemRef("live.camera.01", "live", null, "http://192.168.3.80:81/stream"));

        TabletCue cue1 = new TabletCue(
                "tablet_cue_001", 1, "stagecore_cue_010", 10, "Start main video",
                Arrays.asList(new TabletAction("action_main_01", "main.play", "main.01", 0, 0, true, TabletAction.END_NONE))
        );
        TabletCue cue2 = new TabletCue(
                "tablet_cue_002", 2, "stagecore_cue_014", 14, "Overlay dissolve",
                Arrays.asList(new TabletAction("action_overlay_01", "overlay.play", "overlay.01", 1000, 1000, false, TabletAction.END_CLEAR))
        );
        TabletCue cue3 = new TabletCue(
                "tablet_cue_003", 3, "stagecore_cue_022", 22, "Show live camera",
                Arrays.asList(new TabletAction("action_live_01", "live.show", "live.camera.01", 0, 0, false, TabletAction.END_NONE))
        );
        TabletCue cue4 = new TabletCue(
                "tablet_cue_004", 4, "stagecore_cue_024", 24, "Blackout",
                Arrays.asList(new TabletAction("action_blackout_01", "blackout", null, 0, 0, false, TabletAction.END_NONE))
        );

        activeManifest = new TabletManifest(
                "tablet_manifest/1",
                "project_al_omian_demo",
                "snapshot_demo_001",
                "tablet_manifest_demo_001",
                "العميان",
                media,
                Arrays.asList(cue1, cue2, cue3, cue4)
        );
        activeSource = "bundled sample";
        return activeManifest;
    }

    public TabletManifest activeManifest() {
        if (activeManifest == null) return loadBundledSample();
        return activeManifest;
    }

    public String activeSource() {
        return activeSource;
    }
}
