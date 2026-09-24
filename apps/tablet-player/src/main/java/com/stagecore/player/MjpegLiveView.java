package com.stagecore.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dedicated MJPEG live image layer. Network IO and JPEG decoding never run on
 * the UI thread. A single latest-frame slot drops stale frames under load.
 * Each new URL/hide invalidates callbacks and disconnects the previous stream.
 */
final class MjpegLiveView extends ImageView {
    interface Listener {
        void onReady();
        void onError(String reason);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "stagecore-mjpeg");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicReference<Bitmap> pendingFrame = new AtomicReference<>();
    private final AtomicBoolean framePosted = new AtomicBoolean();
    private volatile HttpURLConnection activeConnection;
    private volatile Listener listener;
    private boolean released;

    MjpegLiveView(Context context) {
        super(context);
        setScaleType(ScaleType.FIT_CENTER);
    }

    void play(String url, Listener listener) {
        stop();
        if (released) return;
        this.listener = listener;
        final int current = generation.get();
        worker.execute(() -> readLoop(url, current));
    }

    void stop() {
        generation.incrementAndGet();
        listener = null;
        HttpURLConnection old = activeConnection;
        if (old != null) old.disconnect();
        pendingFrame.set(null);
        framePosted.set(false);
        setImageDrawable(null);
    }

    void release() {
        if (released) return;
        stop();
        released = true;
        worker.shutdownNow();
    }

    void applyScale(String mode) {
        if (AppSettings.SCALE_CROP.equals(mode)) setScaleType(ScaleType.CENTER_CROP);
        else if (AppSettings.SCALE_FULL.equals(mode)) setScaleType(ScaleType.FIT_XY);
        else setScaleType(ScaleType.FIT_CENTER);
    }

    private void readLoop(String url, int current) {
        int retries = 0;
        boolean ready = false;
        while (isCurrent(current)) {
            HttpURLConnection connection = null;
            try {
                URL source = new URL(url);
                if (!"http".equalsIgnoreCase(source.getProtocol()) || source.getUserInfo() != null) {
                    throw new IOException("Live MJPEG requires an HTTP URL without credentials");
                }
                connection = (HttpURLConnection) source.openConnection();
                activeConnection = connection;
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "multipart/x-mixed-replace");
                int code = connection.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new IOException("MJPEG HTTP " + code);
                }
                MjpegFrameReader reader = new MjpegFrameReader(
                        connection.getInputStream(), connection.getContentType());
                retries = 0;
                while (isCurrent(current)) {
                    byte[] bytes = reader.nextJpeg();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) throw new IOException("Cannot decode MJPEG frame");
                    if (!ready) {
                        ready = true;
                        emitReady(current);
                    }
                    queueFrame(current, bitmap);
                }
            } catch (IOException | RuntimeException error) {
                if (isCurrent(current)) {
                    retries++;
                    emitError(current, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
                    android.util.Log.w("StageCorePlayer", "MJPEG interrupted; retry=" + retries, error);
                }
            } finally {
                if (connection != null) connection.disconnect();
                if (activeConnection == connection) activeConnection = null;
            }
            if (!isCurrent(current)) break;
            try {
                Thread.sleep(Math.min(5000L, 1000L * retries));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void queueFrame(int current, Bitmap bitmap) {
        if (!isCurrent(current)) return;
        pendingFrame.set(bitmap);
        if (!framePosted.compareAndSet(false, true)) return;
        main.post(() -> {
            framePosted.set(false);
            Bitmap latest = pendingFrame.getAndSet(null);
            if (latest != null && isCurrent(current)) setImageBitmap(latest);
        });
    }

    private void emitReady(int current) {
        main.post(() -> {
            if (isCurrent(current) && listener != null) listener.onReady();
        });
    }

    private void emitError(int current, String reason) {
        main.post(() -> {
            if (isCurrent(current) && listener != null) listener.onError(reason);
        });
    }

    private boolean isCurrent(int current) {
        return !released && generation.get() == current && !Thread.currentThread().isInterrupted();
    }
}
