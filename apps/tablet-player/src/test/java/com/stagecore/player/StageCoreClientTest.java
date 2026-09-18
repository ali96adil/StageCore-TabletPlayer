package com.stagecore.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class StageCoreClientTest {
    @Test
    public void advertisedCapabilitiesMatchExecutableV1Contract() {
        List<String> capabilities = new StageCoreClient("tablet-contract-test", "Contract Test").baselineCapabilities();

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
        ), capabilities);

        assertFalse(capabilities.contains("tablet.media.select"));
        assertFalse(capabilities.stream().anyMatch(value -> value.startsWith("tablet.settings.")));
        assertFalse(capabilities.contains("tablet.permissions.check"));
        assertFalse(capabilities.contains("tablet.media.scan"));
        assertFalse(capabilities.contains("tablet.media.prepare_folder"));
        assertFalse(capabilities.stream().anyMatch(value -> value.startsWith("tablet.health.")));
        assertFalse(capabilities.stream().anyMatch(value -> value.startsWith("tablet.alert.")));
    }
}
