package com.stagecore.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class StageCoreClientTest {
    @Test
    public void advertisedCapabilitiesMatchExecutableMediaContract() {
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
    @Test
    public void v2HelloIsProjectIndependent() {
        StageCoreClient client = new StageCoreClient("tablet-001", "Tablet 01");
        String hello = client.hello();

        assertTrue(hello.contains("protocol=stagecore.device/2"));
        assertTrue(hello.contains("device_id=tablet-001"));
        assertTrue(hello.contains("authority=HUB_OWNED_ASSIGNMENT"));
        assertFalse(hello.contains(" project="));
        assertFalse(hello.contains(" snapshot="));
        assertFalse(hello.contains(" manifest="));
    }
}
