package com.stagecore.player;

import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManifestStore {
    private TabletManifest activeManifest;
    private String activeSource = "none";

    public synchronized TabletManifest loadFromDisk(File manifestFile) throws IOException, JSONException {
        if (manifestFile == null) throw new IOException("Manifest file is null");
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            throw new IOException("Manifest file not found: " + manifestFile.getAbsolutePath());
        }
        String json = new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8);
        activeManifest = ManifestJsonCodec.parse(json);
        activeSource = manifestFile.getAbsolutePath();
        return activeManifest;
    }

    public synchronized TabletManifest applyFromStageCore(String json, File manifestFile) throws IOException, JSONException {
        if (json == null || json.trim().isEmpty()) throw new JSONException("Tablet manifest payload is empty");
        if (manifestFile == null) throw new IOException("Manifest file is null");

        // Parse first so malformed network input can never replace the last
        // known-good local manifest.
        TabletManifest candidate = ManifestJsonCodec.parse(json);
        if (!"tablet_manifest/1".equals(candidate.schemaVersion)) {
            throw new JSONException("Unsupported tablet manifest schema: " + candidate.schemaVersion);
        }

        File parent = manifestFile.getParentFile();
        if (parent == null) throw new IOException("Manifest parent directory is unavailable");
        if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IOException("Unable to create manifest directory: " + parent.getAbsolutePath());
        }

        File temporary = new File(parent, manifestFile.getName() + ".stagecore.tmp");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }

        try {
            Files.move(
                    temporary.toPath(),
                    manifestFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary.toPath(), manifestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.exists()) temporary.delete();
        }

        activeManifest = candidate;
        activeSource = "stagecore:" + manifestFile.getAbsolutePath();
        return activeManifest;
    }

    public synchronized TabletManifest tryLoadFromDiskOrSample(File manifestFile) {
        try {
            return loadFromDisk(manifestFile);
        } catch (Exception ignored) {
            return loadBundledSample();
        }
    }

    public synchronized TabletManifest loadBundledSample() {
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

    public synchronized TabletManifest activeManifest() {
        if (activeManifest == null) return loadBundledSample();
        return activeManifest;
    }

    public synchronized String activeSource() {
        return activeSource;
    }
}
