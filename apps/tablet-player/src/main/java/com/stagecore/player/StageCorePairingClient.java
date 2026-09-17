package com.stagecore.player;

import android.os.Build;
import android.util.Base64;

import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Client for StageCore's hardened pairing/challenge/session authority. */
public final class StageCorePairingClient {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient http;
    private final StageCoreDeviceIdentity identity;
    private final String deviceId;
    private final String displayName;

    public StageCorePairingClient(String deviceId, String displayName) {
        this.deviceId = deviceId.trim();
        this.displayName = displayName == null || displayName.trim().isEmpty() ? "StageCore Tablet" : displayName.trim();
        this.identity = new StageCoreDeviceIdentity(this.deviceId);
        this.http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .build();
    }

    public PairingReceipt requestPairing(String baseUrl, java.util.List<String> capabilities) throws Exception {
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        JSONObject body = new JSONObject()
                .put("companion_id", deviceId)
                .put("display_name", displayName)
                .put("hostname", Build.MODEL)
                .put("platform", "android")
                .put("architecture", Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0])
                .put("version", BuildConfig.VERSION_NAME)
                .put("capabilities", new org.json.JSONArray(capabilities))
                .put("public_key_algorithm", StageCoreDeviceIdentity.KEY_ALGORITHM)
                .put("public_key_base64", identity.publicKeyBase64())
                .put("client_nonce_base64", Base64.encodeToString(nonce, Base64.NO_WRAP));
        JSONObject response = post(baseUrl + "/api/v1/companion/pairing/requests", body);
        return new PairingReceipt(response.getString("request_id"), response.getString("pairing_code"));
    }

    public String pairingStatus(String baseUrl, PairingReceipt receipt) throws Exception {
        JSONObject response = post(baseUrl + "/api/v1/companion/pairing/status", new JSONObject()
                .put("request_id", receipt.requestId)
                .put("pairing_code", receipt.pairingCode));
        return response.getString("status");
    }

    public Session authenticate(String baseUrl) throws Exception {
        JSONObject challenge = post(baseUrl + "/api/v1/companion/auth/challenges", new JSONObject()
                .put("companion_id", deviceId));
        String challengeId = challenge.getString("challenge_id");
        String nonce = challenge.getString("nonce_base64");
        String signature = identity.signAuthentication(challengeId, nonce);
        JSONObject session = post(baseUrl + "/api/v1/companion/auth/sessions", new JSONObject()
                .put("companion_id", deviceId)
                .put("challenge_id", challengeId)
                .put("signature_base64", signature));
        return new Session(session.getString("session_id"), session.getString("session_token"), session.optString("expires_at", ""));
    }

    private JSONObject post(String url, JSONObject json) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json.toString(), JSON))
                .build();
        try (Response response = http.newCall(request).execute()) {
            String raw = response.body() == null ? "{}" : response.body().string();
            JSONObject object = new JSONObject(raw.isEmpty() ? "{}" : raw);
            if (!response.isSuccessful()) {
                throw new StageCoreAuthException(response.code(), object.optString("error_code", "HTTP_" + response.code()));
            }
            return object;
        }
    }

    public static final class PairingReceipt {
        public final String requestId;
        public final String pairingCode;
        PairingReceipt(String requestId, String pairingCode) {
            this.requestId = requestId;
            this.pairingCode = pairingCode;
        }
    }

    public static final class Session {
        public final String sessionId;
        public final String token;
        public final String expiresAt;
        Session(String sessionId, String token, String expiresAt) {
            this.sessionId = sessionId;
            this.token = token;
            this.expiresAt = expiresAt;
        }
    }

    public static final class StageCoreAuthException extends Exception {
        public final int httpStatus;
        public final String errorCode;
        StageCoreAuthException(int httpStatus, String errorCode) {
            super(errorCode);
            this.httpStatus = httpStatus;
            this.errorCode = errorCode;
        }
    }
}
