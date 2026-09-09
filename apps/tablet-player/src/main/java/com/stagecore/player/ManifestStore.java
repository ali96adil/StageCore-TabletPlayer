package com.stagecore.player;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManifestStore {
    private TabletManifest activeManifest;

    public TabletManifest loadBundledSample() {
        Map<String, MediaItemRef> media = new LinkedHashMap<>();
        media.put("main.01", new MediaItemRef("main.01", "main", "main_01.mp4", null));
        media.put("overlay.01", new MediaItemRef("overlay.01", "overlay", "overlay_01.mp4", null));
        media.put("live.camera.01", new MediaItemRef("live.camera.01", "live", null, "http://192.168.3.80:81/stream"));

        TabletCue cue1 = new TabletCue(
                "tablet_cue_001", 1, "stagecore_cue_010", 10, "Start main video",
                Arrays.asList(new TabletAction("action_main_01", "main.play", "main.01", 0, 0))
        );
        TabletCue cue2 = new TabletCue(
                "tablet_cue_002", 2, "stagecore_cue_014", 14, "Overlay dissolve",
                Arrays.asList(new TabletAction("action_overlay_01", "overlay.play", "overlay.01", 1000, 1000))
        );
        TabletCue cue3 = new TabletCue(
                "tablet_cue_003", 3, "stagecore_cue_022", 22, "Show live camera",
                Arrays.asList(new TabletAction("action_live_01", "live.show", "live.camera.01", 0, 0))
        );

        activeManifest = new TabletManifest(
                "tablet_manifest/1",
                "project_al_omian_demo",
                "snapshot_demo_001",
                "tablet_manifest_demo_001",
                "العميان",
                media,
                Arrays.asList(cue1, cue2, cue3)
        );
        return activeManifest;
    }

    public TabletManifest activeManifest() {
        if (activeManifest == null) return loadBundledSample();
        return activeManifest;
    }
}
