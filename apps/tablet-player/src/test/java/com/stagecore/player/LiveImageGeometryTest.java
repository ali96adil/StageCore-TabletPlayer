package com.stagecore.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LiveImageGeometryTest {
    @Test public void fitAfterQuarterTurnPreservesAllImagePixels() {
        // Landscape 640x480 camera rotated into 480x640 portrait.
        LiveImageGeometry result = LiveImageGeometry.of(640, 480, 800, 1280,
                90, AppSettings.SCALE_FIT);
        assertEquals(800f / 480f, result.scaleX, 0.001f);
        assertEquals(result.scaleX, result.scaleY, 0.001f);
        assertEquals(800f, 480f * result.scaleX, 0.01f);
        assertEquals(1066.667f, 640f * result.scaleY, 0.01f);
    }

    @Test public void cropAfterQuarterTurnFillsPortraitWithoutStretching() {
        LiveImageGeometry result = LiveImageGeometry.of(640, 480, 800, 1280,
                270, AppSettings.SCALE_CROP);
        assertEquals(2f, result.scaleX, 0.001f);
        assertEquals(2f, result.scaleY, 0.001f);
    }

    @Test public void fullAfterQuarterTurnMayStretchToFill() {
        LiveImageGeometry result = LiveImageGeometry.of(640, 480, 800, 1280,
                90, AppSettings.SCALE_FULL);
        assertEquals(800f / 480f, result.scaleX, 0.001f);
        assertEquals(2f, result.scaleY, 0.001f);
    }

    @Test public void zeroAndHalfTurnKeepLandscapeAspect() {
        for (int rotation : new int[] {0, 180}) {
            LiveImageGeometry result = LiveImageGeometry.of(640, 480, 800, 1280,
                    rotation, AppSettings.SCALE_FIT);
            assertEquals(1.25f, result.scaleX, 0.001f);
            assertEquals(result.scaleX, result.scaleY, 0.001f);
        }
    }

    @Test public void unknownRotationFallsBackToZero() {
        assertEquals(0, AppSettings.normalizeLiveRotation(-90));
        assertEquals(0, AppSettings.normalizeLiveRotation(45));
        assertEquals(0, AppSettings.normalizeLiveRotation(360));
        assertEquals(270, AppSettings.normalizeLiveRotation(270));
    }
}
