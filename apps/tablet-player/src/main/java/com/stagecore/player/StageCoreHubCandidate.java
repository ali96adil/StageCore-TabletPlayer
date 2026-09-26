package com.stagecore.player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Authenticated StageCore Hub candidate parsed from the F-004 Bonjour TXT contract. */
public final class StageCoreHubCandidate {
    public static final String SERVICE_TYPE = "_stagecore-hub._tcp.";

    public final String hubId;
    public final String displayName;
    public final String fingerprint;
    public final String tlsCertificateSha256;
    public final String advertisedHost;
    public final String resolvedHost;
    public final int port;

    private StageCoreHubCandidate(
            String hubId,
            String displayName,
            String fingerprint,
            String tlsCertificateSha256,
            String advertisedHost,
            String resolvedHost,
            int port) {
        this.hubId = hubId;
        this.displayName = displayName;
        this.fingerprint = fingerprint;
        this.tlsCertificateSha256 = tlsCertificateSha256;
        this.advertisedHost = advertisedHost;
        this.resolvedHost = resolvedHost;
        this.port = port;
    }

    public static StageCoreHubCandidate fromTxt(
            Map<String, String> txt,
            String resolvedHost,
            int resolvedPort,
            String serviceType) {
        if (!SERVICE_TYPE.equals(serviceType)) {
            throw new IllegalArgumentException("unsupported StageCore discovery service");
        }
        if (txt == null || !"1".equals(trim(txt.get("v")))) {
            throw new IllegalArgumentException("unsupported StageCore discovery version");
        }

        String rawHubId = trim(txt.get("hub_id"));
        final String hubId;
        try {
            hubId = UUID.fromString(rawHubId).toString().toLowerCase(Locale.US);
        } catch (Exception error) {
            throw new IllegalArgumentException("invalid Hub ID", error);
        }

        String displayName = trim(txt.get("name"));
        if (displayName.isEmpty() || displayName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 96) {
            throw new IllegalArgumentException("invalid Hub display name");
        }

        String fingerprint = trim(txt.get("hub_fp"));
        if (fingerprint.isEmpty() || fingerprint.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 200) {
            throw new IllegalArgumentException("invalid Hub fingerprint");
        }

        String pin = trim(txt.get("tls_sha256")).toLowerCase(Locale.US);
        if (!isValidCertificateSha256(pin)) {
            throw new IllegalArgumentException("invalid Hub TLS certificate pin");
        }

        String advertisedHost = trim(txt.get("host")).toLowerCase(Locale.US);
        if (!isValidLocalHost(advertisedHost)) {
            throw new IllegalArgumentException("invalid advertised Hub host");
        }

        int txtPort;
        try {
            txtPort = Integer.parseInt(trim(txt.get("port")));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid advertised Hub port", error);
        }
        if (txtPort < 1 || txtPort > 65535 || resolvedPort != txtPort) {
            throw new IllegalArgumentException("resolved Hub port does not match advertisement");
        }

        if (!"/".equals(trim(txt.get("api_path")))
                || !"/api/v1/companion/runtime".equals(trim(txt.get("runtime_path")))) {
            throw new IllegalArgumentException("invalid Hub discovery paths");
        }

        String endpoint = trim(resolvedHost);
        if (endpoint.isEmpty() || endpoint.contains("/") || endpoint.contains("\\")
                || endpoint.contains("@") || containsWhitespace(endpoint)) {
            throw new IllegalArgumentException("invalid resolved Hub endpoint");
        }

        return new StageCoreHubCandidate(
                hubId,
                displayName,
                fingerprint,
                pin,
                advertisedHost,
                endpoint,
                resolvedPort);
    }

    public boolean matchesBinding(String rememberedHubId, String rememberedFingerprint, String rememberedPin) {
        return hubId.equals(trim(rememberedHubId).toLowerCase(Locale.US))
                && fingerprint.equals(trim(rememberedFingerprint))
                && tlsCertificateSha256.equals(trim(rememberedPin).toLowerCase(Locale.US));
    }

    static boolean isValidCertificateSha256(String value) {
        String normalized = trim(value).toLowerCase(Locale.US);
        if (normalized.length() != 64) return false;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (!((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f'))) return false;
        }
        return true;
    }

    private static boolean isValidLocalHost(String host) {
        if (host.isEmpty() || host.length() > 253 || !host.endsWith(".local")
                || host.contains("/") || host.contains("\\") || host.contains(":")
                || host.contains("@") || containsWhitespace(host)) {
            return false;
        }
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2 || !"local".equals(labels[labels.length - 1])) return false;
        for (int i = 0; i < labels.length - 1; i++) {
            String label = labels[i];
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
            for (int j = 0; j < label.length(); j++) {
                char ch = label.charAt(j);
                if (!((ch >= 'a' && ch <= 'z')
                        || (ch >= '0' && ch <= '9')
                        || ch == '-')) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
