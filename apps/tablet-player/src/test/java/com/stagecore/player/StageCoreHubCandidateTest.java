package com.stagecore.player;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class StageCoreHubCandidateTest {
    @Test
    public void parsesExactF004Advertisement() {
        StageCoreHubCandidate hub = StageCoreHubCandidate.fromTxt(
                validTxt(), "192.168.3.130", 7841, StageCoreHubCandidate.SERVICE_TYPE);

        assertEquals("01234567-89ab-cdef-8123-456789abcdef", hub.hubId);
        assertEquals("StageCore Hub", hub.displayName);
        assertEquals("192.168.3.130", hub.resolvedHost);
        assertEquals(7841, hub.port);
        assertTrue(hub.matchesBinding(
                "01234567-89ab-cdef-8123-456789abcdef",
                "SHA256:example",
                repeat('a', 64)));
    }

    @Test
    public void rememberedBindingReconstructsPinnedEndpoint() {
        StageCoreHubCandidate hub = StageCoreHubCandidate.remembered(
                "01234567-89ab-cdef-8123-456789abcdef",
                "SHA256:example",
                repeat('a', 64),
                "192.168.3.130",
                7841);

        assertEquals("https://192.168.3.130:7841", hub.baseUrl());
        assertTrue(hub.matchesBinding(
                "01234567-89ab-cdef-8123-456789abcdef",
                "SHA256:example",
                repeat('a', 64)));
    }

    @Test
    public void rejectsLegacyServiceAndPinMismatch() {
        assertThrows(IllegalArgumentException.class, () ->
                StageCoreHubCandidate.fromTxt(validTxt(), "192.168.3.130", 7841, "_stagecore._tcp."));

        Map<String, String> bad = validTxt();
        bad.put("tls_sha256", "not-a-pin");
        assertThrows(IllegalArgumentException.class, () ->
                StageCoreHubCandidate.fromTxt(bad, "192.168.3.130", 7841, StageCoreHubCandidate.SERVICE_TYPE));
    }

    @Test
    public void rejectsResolvedPortDifferentFromTxt() {
        assertThrows(IllegalArgumentException.class, () ->
                StageCoreHubCandidate.fromTxt(validTxt(), "192.168.3.130", 9000, StageCoreHubCandidate.SERVICE_TYPE));
    }

    private static Map<String, String> validTxt() {
        Map<String, String> txt = new LinkedHashMap<>();
        txt.put("v", "1");
        txt.put("hub_id", "01234567-89ab-cdef-8123-456789abcdef");
        txt.put("name", "StageCore Hub");
        txt.put("hub_fp", "SHA256:example");
        txt.put("tls_sha256", repeat('a', 64));
        txt.put("host", "stagecore-0123456789ab.local");
        txt.put("port", "7841");
        txt.put("api_path", "/");
        txt.put("runtime_path", "/api/v1/companion/runtime");
        return txt;
    }

    private static String repeat(char ch, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(ch);
        return out.toString();
    }
}
