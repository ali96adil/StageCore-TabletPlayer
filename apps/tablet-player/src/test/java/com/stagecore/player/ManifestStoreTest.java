package com.stagecore.player;

import com.stagecore.player.model.TabletManifest;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class ManifestStoreTest {
    @Test
    public void missingManifestAutoDiscoversNumberedMainAndOverlayMedia() throws Exception {
        File folder = Files.createTempDirectory("stagecore-media").toFile();
        touch(folder, "main_01.mp4");
        touch(folder, "main_02.mp4");
        touch(folder, "main_03.mp4");
        touch(folder, "overlay_01.mp4");
        touch(folder, "overlay_02.mp4");
        touch(folder, "overlay_03.mp4");
        touch(folder, "ignore_me.mp4");

        ManifestStore store = new ManifestStore();
        TabletManifest manifest = store.tryLoadFromDiskOrSample(
                new File(folder, MediaResolver.MANIFEST_FILE_NAME));

        assertEquals("auto-discovered TheatreVideos", store.activeSource());
        assertEquals(6, manifest.media.size());
        assertEquals("main_01.mp4", manifest.media.get("main.01").file);
        assertEquals("main_02.mp4", manifest.media.get("main.02").file);
        assertEquals("main_03.mp4", manifest.media.get("main.03").file);
        assertEquals("overlay_01.mp4", manifest.media.get("overlay.01").file);
        assertEquals("overlay_02.mp4", manifest.media.get("overlay.02").file);
        assertEquals("overlay_03.mp4", manifest.media.get("overlay.03").file);
        assertEquals(0, manifest.cues.size());
    }

    @Test
    public void autoDiscoveryNormalizesNumericSuffixToMediaKey() throws Exception {
        File folder = Files.createTempDirectory("stagecore-media-number").toFile();
        touch(folder, "main_2.mp4");
        touch(folder, "overlay_12.mp4");

        ManifestStore store = new ManifestStore();
        TabletManifest manifest = store.tryLoadFromDiskOrSample(
                new File(folder, MediaResolver.MANIFEST_FILE_NAME));

        assertNotNull(manifest.media.get("main.02"));
        assertEquals("main_2.mp4", manifest.media.get("main.02").file);
        assertNotNull(manifest.media.get("overlay.12"));
        assertEquals("overlay_12.mp4", manifest.media.get("overlay.12").file);
    }

    @Test
    public void missingManifestWithNoRecognizedMediaKeepsBundledSampleFallback() throws Exception {
        File folder = Files.createTempDirectory("stagecore-media-empty").toFile();
        touch(folder, "notes.txt");

        ManifestStore store = new ManifestStore();
        TabletManifest manifest = store.tryLoadFromDiskOrSample(
                new File(folder, MediaResolver.MANIFEST_FILE_NAME));

        assertEquals("bundled sample", store.activeSource());
        assertNotNull(manifest.media.get("main.01"));
        assertNotNull(manifest.media.get("overlay.01"));
    }

    private static void touch(File folder, String name) throws Exception {
        File file = new File(folder, name);
        if (!file.createNewFile()) {
            throw new IllegalStateException("Could not create " + file);
        }
    }
}
