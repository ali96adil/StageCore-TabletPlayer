package com.stagecore.player;

import org.junit.Test;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class StageCoreHubSecurityTest {
    @Test
    public void exactDerDigestMatchesAndDifferentDigestFails() throws Exception {
        byte[] der = "stagecore-test-certificate".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String pin = hex(MessageDigest.getInstance("SHA-256").digest(der));

        assertTrue(StageCoreHubTransport.certificatePinMatches(pin, der));
        assertFalse(StageCoreHubTransport.certificatePinMatches(repeat('0', 64), der));
        assertFalse(StageCoreHubTransport.certificatePinMatches("bad", der));
    }

    @Test
    public void endpointVerifierInputRejectsSchemesAndPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> StageCoreHubTransport.normalizeEndpointHost("https://192.168.3.130"));
        assertThrows(IllegalArgumentException.class,
                () -> StageCoreHubTransport.normalizeEndpointHost("192.168.3.130/path"));
    }

    @Test
    public void publicIdentityMustMatchAdvertisedHub() throws Exception {
        StageCoreHubCandidate hub = StageCoreHubCandidate.fromTxt(
                validTxt(), "192.168.3.130", 7841, StageCoreHubCandidate.SERVICE_TYPE);

        StageCoreHubIdentityVerifier.validate(
                1,
                hub.hubId,
                hub.fingerprint,
                "StageCore Hub",
                hub);

        assertThrows(StageCoreHubIdentityVerifier.HubIdentityException.class, () ->
                StageCoreHubIdentityVerifier.validate(
                        1,
                        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                        hub.fingerprint,
                        "StageCore Hub",
                        hub));
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

    private static String hex(byte[] value) {
        StringBuilder out = new StringBuilder(value.length * 2);
        for (byte b : value) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static String repeat(char ch, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(ch);
        return out.toString();
    }
}
