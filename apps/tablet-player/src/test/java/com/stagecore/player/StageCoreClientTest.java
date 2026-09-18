package com.stagecore.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StageCoreClientTest {
    @Test
    public void advertisedMediaCapabilitiesMatchExecutableV1Contract() {
        List<String> capabilities = new StageCoreClient("tablet-contract-test", "Contract Test").baselineCapabilities();
        List<String> actualMedia = new ArrayList<>();
        for (String capability : capabilities) {
            if (capability.startsWith("tablet.media.")) {
                actualMedia.add(capability);
            }
        }

        assertEquals(Arrays.asList(
                "tablet.media.prepare",
                "tablet.media.play",
                "tablet.media.pause",
                "tablet.media.stop",
                "tablet.media.blackout",
                "tablet.media.blackout.clear",
                "tablet.media.overlay.play",
                "tablet.media.overlay.clear",
                "tablet.media.live.show",
                "tablet.media.live.hide"
        ), actualMedia);
        assertFalse(capabilities.contains("tablet.media.select"));
    }
}
