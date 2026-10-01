package com.stagecore.player;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Verifies the public Hub identity only after the advertised TLS leaf pin is active. */
public final class StageCoreHubIdentityVerifier {
    private StageCoreHubIdentityVerifier() {}

    public static void verify(
            String baseUrl,
            OkHttpClient pinnedClient,
            StageCoreHubCandidate candidate) throws Exception {
        OkHttpClient client = pinnedClient.newBuilder()
                .readTimeout(3, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(trimSlash(baseUrl) + "/api/v1/hub/identity")
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HubIdentityException("Hub identity HTTP " + response.code());
            }
            String raw = response.body() == null ? "{}" : response.body().string();
            JSONObject body = new JSONObject(raw);
            validate(
                    body.optInt("schema_version", -1),
                    body.optString("hub_id", ""),
                    body.optString("fingerprint", ""),
                    body.optString("display_name", ""),
                    candidate);
        }
    }

    static void validate(
            int schemaVersion,
            String hubId,
            String fingerprint,
            String displayName,
            StageCoreHubCandidate candidate) throws HubIdentityException {
        if (candidate == null
                || schemaVersion != 1
                || !candidate.hubId.equals(hubId == null ? "" : hubId.trim().toLowerCase(java.util.Locale.US))
                || !candidate.fingerprint.equals(fingerprint == null ? "" : fingerprint.trim())
                || displayName == null
                || displayName.trim().isEmpty()) {
            throw new HubIdentityException("StageCore Hub identity mismatch");
        }
    }

    private static String trimSlash(String value) {
        String out = value == null ? "" : value.trim();
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }

    public static final class HubIdentityException extends Exception {
        HubIdentityException(String message) {
            super(message);
        }
    }
}
