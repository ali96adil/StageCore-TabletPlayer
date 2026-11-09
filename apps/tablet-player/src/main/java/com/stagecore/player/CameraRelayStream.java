package com.stagecore.player;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** One protected MJPEG attempt, bound to the assignment and socket that requested it. */
final class CameraRelayStream {
    static final String MEDIA_KEY = "stagecore.camera.relay.v1";
    private final OkHttpClient client;
    private final Request request;
    private final Instant expiresAt;
    private final BooleanSupplier scopeCurrent;
    private final Runnable renewSession;
    private volatile boolean cancelled;
    private Call call;
    private Runnable onCancel;

    CameraRelayStream(OkHttpClient pairedTransport, String baseUrl, String projectId, String token,
                      Instant expiresAt, BooleanSupplier scopeCurrent, Runnable renewSession) {
        client = pairedTransport.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        request = request(baseUrl, projectId, token);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.scopeCurrent = Objects.requireNonNull(scopeCurrent);
        this.renewSession = Objects.requireNonNull(renewSession);
    }

    static Request request(String baseUrl, String projectId, String token) {
        HttpUrl base = HttpUrl.get(baseUrl);
        if (!base.isHttps() || !base.username().isEmpty() || !base.password().isEmpty()
                || !base.encodedPath().equals("/") || base.query() != null || base.fragment() != null
                || projectId == null || !projectId.matches("[a-zA-Z0-9_-]{1,128}")
                || token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Invalid protected camera scope");
        }
        HttpUrl url = base.newBuilder().addPathSegments("api/v1/stage-device-camera/projects")
                .addPathSegment(projectId).addPathSegment("stream").build();
        return new Request.Builder().url(url)
                .header("Authorization", "StageCoreSession " + token)
                .header("Accept", "multipart/x-mixed-replace")
                .build();
    }

    boolean isCurrent() {
        return !cancelled && scopeCurrent.getAsBoolean() && Instant.now().isBefore(expiresAt);
    }

    void whenCancelled(Runnable listener) {
        synchronized (this) {
            if (!cancelled) {
                onCancel = listener;
                return;
            }
        }
        listener.run();
    }

    Response open() throws IOException {
        if (!isCurrent()) {
            terminate(!Instant.now().isBefore(expiresAt));
            throw new IOException("Camera Relay authority ended");
        }
        Call next = client.newCall(request);
        synchronized (this) {
            if (cancelled) throw new IOException("Camera Relay authority ended");
            call = next;
        }
        Response response = next.execute();
        if (!isCurrent() || response.code() != 200) {
            int code = response.code();
            response.close();
            if (!isCurrent() || code == 401 || code == 403 || code == 404 || code == 410
                    || (code >= 300 && code < 400)) {
                terminate(code == 401 || code == 410 || !Instant.now().isBefore(expiresAt));
            }
            throw new IOException("Camera Relay HTTP " + code);
        }
        return response;
    }

    void terminateIfScopeEnded() {
        if (!isCurrent()) terminate(!Instant.now().isBefore(expiresAt));
    }

    void cancel() {
        terminate(false);
    }

    private void terminate(boolean renew) {
        Runnable listener;
        Call previous;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            previous = call;
            call = null;
            listener = onCancel;
            onCancel = null;
        }
        if (previous != null) previous.cancel();
        if (listener != null) listener.run();
        if (renew && scopeCurrent.getAsBoolean()) renewSession.run();
    }
}
