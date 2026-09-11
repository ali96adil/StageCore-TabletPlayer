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
import java.util.LinkedHashMap;
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
            return loadBundledSample();
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
