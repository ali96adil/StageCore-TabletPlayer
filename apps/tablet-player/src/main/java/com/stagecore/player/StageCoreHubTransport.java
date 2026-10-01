package com.stagecore.player;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/**
 * Builds the shared HTTPS/WSS transport for a remembered StageCore Hub.
 *
 * The Hub advertises SHA-256 of the exact self-signed leaf DER. We therefore
 * trust only that exact leaf and bind OkHttp hostname acceptance to the exact
 * endpoint selected by authenticated discovery. No wildcard verifier or
 * trust-all manager is used.
 */
public final class StageCoreHubTransport {
    private StageCoreHubTransport() {}

    public static OkHttpClient makeClient(String expectedHost, String tlsCertificateSha256)
            throws GeneralSecurityException {
        String host = normalizeEndpointHost(expectedHost);
        String pin = normalizePin(tlsCertificateSha256);
        PinnedTrustManager trustManager = new PinnedTrustManager(pin);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustManager}, null);

        return new OkHttpClient.Builder()
                .sslSocketFactory(context.getSocketFactory(), trustManager)
                .hostnameVerifier((hostname, session) -> host.equalsIgnoreCase(hostname))
                .connectTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    static boolean certificatePinMatches(String expectedSha256, byte[] certificateDer) {
        if (certificateDer == null) return false;
        String expected;
        try {
            expected = normalizePin(expectedSha256);
        } catch (IllegalArgumentException error) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] actual = digest.digest(certificateDer);
            byte[] expectedBytes = decodeHex(expected);
            return MessageDigest.isEqual(actual, expectedBytes);
        } catch (GeneralSecurityException error) {
            return false;
        }
    }

    static String normalizeEndpointHost(String value) {
        String host = value == null ? "" : value.trim();
        if (host.isEmpty() || host.contains("://") || host.contains("/")
                || host.contains("\\") || host.contains("@")) {
            throw new IllegalArgumentException("invalid StageCore Hub endpoint host");
        }
        for (int i = 0; i < host.length(); i++) {
            if (Character.isWhitespace(host.charAt(i))) {
                throw new IllegalArgumentException("invalid StageCore Hub endpoint host");
            }
        }
        return host;
    }

    private static String normalizePin(String value) {
        String pin = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (!StageCoreHubCandidate.isValidCertificateSha256(pin)) {
            throw new IllegalArgumentException("invalid StageCore Hub TLS pin");
        }
        return pin;
    }

    private static byte[] decodeHex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(value.charAt(i * 2), 16);
            int lo = Character.digit(value.charAt(i * 2 + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static final class PinnedTrustManager implements X509TrustManager {
        private final String expectedSha256;

        private PinnedTrustManager(String expectedSha256) {
            this.expectedSha256 = expectedSha256;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            throw new CertificateException("client certificates are not accepted");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            if (chain == null || chain.length == 0 || chain[0] == null) {
                throw new CertificateException("StageCore Hub did not present a certificate");
            }
            X509Certificate leaf = chain[0];
            leaf.checkValidity();
            try {
                if (!certificatePinMatches(expectedSha256, leaf.getEncoded())) {
                    throw new CertificateException("StageCore Hub TLS pin mismatch");
                }
            } catch (CertificateException error) {
                throw error;
            } catch (Exception error) {
                throw new CertificateException("unable to inspect StageCore Hub certificate", error);
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
