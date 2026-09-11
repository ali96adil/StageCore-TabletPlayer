package com.stagecore.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.stagecore.player.model.CommandResult;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class TabletPlayer {
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout stageView;
    private TextureSlot mainVideo;
    private TextureSlot overlayVideo;
    private TextureSlot liveVideo;
    private View blackoutView;
    private TextView statusView;
    private File preparedMainFile;
    private String currentMain = "none";
    private String preparedMain = "none";
    private String currentOverlay = "none";
    private String currentLive = "none";
    private boolean mainPlaying = false;
    private boolean blackoutVisible = false;
    private boolean statusPinned = false;
    private String videoScaleMode = AppSettings.SCALE_FIT;

    public TabletPlayer(Context context) {
        this.context = context;
    }

    public void attachTo(FrameLayout stage) {
        stageView = stage;
        stageView.setBackgroundColor(Color.BLACK);
        mainVideo = new TextureSlot("main");
        overlayVideo = new TextureSlot("overlay");
        liveVideo = new TextureSlot("live");
        blackoutView = new View(context);
        statusView = new TextView(context);

        overlayVideo.view.setVisibility(View.GONE);
        liveVideo.view.setVisibility(View.GONE);

        blackoutView.setBackgroundColor(Color.BLACK);
        blackoutView.setVisibility(View.GONE);

        statusView.setTextColor(Color.WHITE);
        statusView.setBackgroundColor(0x66000000);
        statusView.setPadding(18, 12, 18, 12);
        statusView.setText("StageCore Player ready");
        statusView.setVisibility(View.GONE);

        stage.addView(mainVideo.view, fillParams());
        stage.addView(overlayVideo.view, fillParams());
        stage.addView(liveVideo.view, fillParams());
        stage.addView(blackoutView, fillParams());

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        stage.addView(statusView, statusParams);
    }

    public void setVideoScaleMode(String mode) {
        if (AppSettings.SCALE_FULL.equals(mode) || AppSettings.SCALE_FIT.equals(mode) || AppSettings.SCALE_CROP.equals(mode)) {
            videoScaleMode = mode;
        } else {
            videoScaleMode = AppSettings.SCALE_FIT;
        }
        applyKnownLayouts();
    }

    public String videoScaleMode() {
        return videoScaleMode;
    }

    public CommandResult prepareMain(File file) {
        if (!isReadableFile(file)) return CommandResult.failed("MEDIA_NOT_FOUND", missing(file));
        preparedMainFile = file;
        preparedMain = file.getName();
        mainVideo.view.clearAnimation();
        mainVideo.view.setVisibility(View.VISIBLE);
        mainVideo.prepare(Uri.fromFile(file), true, false, 1, null);
        showStatus("Prepared main: " + preparedMain);
        return CommandResult.completed("Prepared main " + file.getName());
    }

    public CommandResult playPreparedMain() {
        if (preparedMainFile == null) return CommandResult.failed("MAIN_NOT_PREPARED", "No prepared main media");
        return playMain(preparedMainFile);
    }

    public CommandResult playMain(File file) {
        if (!isReadableFile(file)) return CommandResult.failed("MEDIA_NOT_FOUND", missing(file));
        hideBlackout();
        hideLive();
        mainVideo.view.clearAnimation();
        mainVideo.view.setAlpha(1f);
        mainVideo.view.setVisibility(View.VISIBLE);
        currentMain = file.getName();
        preparedMainFile = file;
        preparedMain = file.getName();
        mainPlaying = false;
        mainVideo.prepare(Uri.fromFile(file), true, true, 0, () -> {
            mainPlaying = true;
            showStatus("Playing main: " + currentMain);
        });
        showStatus("Loading main: " + currentMain);
        return CommandResult.completed("Playing main " + file.getName());
    }

    public CommandResult pauseMain() {
        if (mainVideo != null && mainVideo.isPlaying()) {
            mainVideo.pause();
            mainPlaying = false;
            showStatus("Main paused");
            return CommandResult.completed("Main paused");
        }
        mainPlaying = false;
        showStatus("Main already paused");
        return CommandResult.completed("Main already paused");
    }

    public CommandResult stopMain() {
        if (mainVideo != null) mainVideo.stopAndReset();
        mainPlaying = false;
        currentMain = "none";
        preparedMain = "none";
        preparedMainFile = null;
        showStatus("Main stopped");
        return CommandResult.completed("Main stopped");
    }

    public CommandResult playOverlay(File file, int dissolveInMs, int dissolveOutMs) {
        if (!isReadableFile(file)) return CommandResult.failed("MEDIA_NOT_FOUND", missing(file));
        overlayVideo.view.animate().cancel();
        overlayVideo.view.setAlpha(dissolveInMs <= 0 ? 1f : 0f);
        overlayVideo.view.setVisibility(View.VISIBLE);
        overlayVideo.view.bringToFront();
        keepControlsOnTop();
        currentOverlay = file.getName();
        hideBlackout();
        overlayVideo.prepare(Uri.fromFile(file), false, true, 0, () -> {
            if (dissolveInMs > 0) overlayVideo.view.animate().alpha(1f).setDuration(dissolveInMs).start();
        }, () -> hideOverlay(dissolveOutMs));
        showStatus("Overlay: " + currentOverlay);
        return CommandResult.completed("Playing overlay " + file.getName());
    }

    public CommandResult hideOverlay(int dissolveOutMs) {
        if (overlayVideo == null || overlayVideo.view.getVisibility() != View.VISIBLE) {
            currentOverlay = "none";
            return CommandResult.completed("Overlay already hidden");
        }
        overlayVideo.view.animate().cancel();
        if (dissolveOutMs <= 0) {
            overlayVideo.stopAndReset();
            overlayVideo.view.setVisibility(View.GONE);
            currentOverlay = "none";
            showStatus("Overlay hidden");
            return CommandResult.completed("Overlay hidden");
        }
        overlayVideo.view.animate().alpha(0f).setDuration(dissolveOutMs).withEndAction(() -> {
            overlayVideo.stopAndReset();
            overlayVideo.view.setVisibility(View.GONE);
            currentOverlay = "none";
            showStatus("Overlay hidden");
        }).start();
        return CommandResult.completed("Overlay hide requested");
    }

    public CommandResult showLive(String url) {
        if (url == null || url.trim().isEmpty()) return CommandResult.failed("LIVE_URL_MISSING", "Live URL is missing");
        hideBlackout();
        hideOverlay(0);
        liveVideo.view.setAlpha(1f);
        liveVideo.view.setVisibility(View.VISIBLE);
        liveVideo.view.bringToFront();
        keepControlsOnTop();
        currentLive = url;
        liveVideo.prepare(Uri.parse(url), false, true, 0, null);
        showStatus("Live: " + url);
        return CommandResult.completed("Live visible");
    }

    public CommandResult hideLive() {
        if (liveVideo != null) {
            liveVideo.stopAndReset();
            liveVideo.view.setVisibility(View.GONE);
        }
        currentLive = "none";
        showStatus("Live hidden");
        return CommandResult.completed("Live hidden");
    }

    public CommandResult blackout() {
        blackoutVisible = true;
        blackoutView.setVisibility(View.VISIBLE);
        blackoutView.bringToFront();
        keepControlsOnTop();
        showStatus("BLACKOUT");
        return CommandResult.completed("Blackout visible");
    }

    public CommandResult clearBlackout() {
        hideBlackout();
        showStatus("Blackout cleared");
        return CommandResult.completed("Blackout cleared");
    }

    public CommandResult identify() {
        if (statusView == null) return CommandResult.completed("Identified");
        boolean wasPinned = statusPinned;
        statusView.setVisibility(View.VISIBLE);
        statusView.bringToFront();
        showStatus("IDENTIFY - StageCore Player");
        statusView.setBackgroundColor(0xCCFFFFFF);
        statusView.setTextColor(Color.BLACK);
        mainHandler.postDelayed(() -> {
            statusView.setBackgroundColor(0x66000000);
            statusView.setTextColor(Color.WHITE);
            if (!wasPinned && !statusPinned) statusView.setVisibility(View.GONE);
        }, 1200);
        return CommandResult.completed("Identified");
    }

    public void setStatusVisible(boolean visible) {
        statusPinned = visible;
        if (statusView != null) statusView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public String observationSummary() {
        return "main=" + currentMain
                + " prepared=" + preparedMain
                + " playing=" + mainPlaying
                + " overlay=" + currentOverlay
                + " live=" + currentLive
                + " blackout=" + blackoutVisible
                + " scale=" + videoScaleMode;
    }

    private void keepControlsOnTop() {
        if (blackoutView != null && blackoutView.getVisibility() == View.VISIBLE) blackoutView.bringToFront();
        if (statusView != null) statusView.bringToFront();
    }

    private void hideBlackout() {
        blackoutVisible = false;
        blackoutView.setVisibility(View.GONE);
    }

    private boolean isReadableFile(File file) {
        return file != null && file.exists() && file.isFile() && file.canRead();
    }

    private String missing(File file) {
        return "Missing media: " + (file == null ? "null" : file.getAbsolutePath());
    }

    private void showStatus(String message) {
        if (statusView != null) statusView.setText(message + "\n" + observationSummary());
    }

    private FrameLayout.LayoutParams fillParams() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
        );
    }

    private void applyKnownLayouts() {
        if (mainVideo != null) mainVideo.applyLayout();
        if (overlayVideo != null) overlayVideo.applyLayout();
        if (liveVideo != null) liveVideo.applyLayout();
    }

    private void applyVideoLayout(TextureView video, int videoWidth, int videoHeight) {
        if (video == null || stageView == null) return;
        int stageWidth = stageView.getWidth();
        int stageHeight = stageView.getHeight();
        if (stageWidth <= 0 || stageHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            video.setLayoutParams(fillParams());
            return;
        }
        if (AppSettings.SCALE_FULL.equals(videoScaleMode)) {
            video.setLayoutParams(fillParams());
            return;
        }

        float scaleFit = Math.min(stageWidth / (float) videoWidth, stageHeight / (float) videoHeight);
        float scaleCrop = Math.max(stageWidth / (float) videoWidth, stageHeight / (float) videoHeight);
        float scale = AppSettings.SCALE_CROP.equals(videoScaleMode) ? scaleCrop : scaleFit;
        int targetWidth = Math.max(1, Math.round(videoWidth * scale));
        int targetHeight = Math.max(1, Math.round(videoHeight * scale));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.CENTER);
        video.setLayoutParams(params);
        android.util.Log.i("StageCorePlayer", String.format(Locale.US,
                "texture scale mode=%s stage=%dx%d video=%dx%d target=%dx%d",
                videoScaleMode, stageWidth, stageHeight, videoWidth, videoHeight, targetWidth, targetHeight));
    }

    private final class TextureSlot implements TextureView.SurfaceTextureListener {
        final String name;
        final TextureView view;
        private Surface surface;
        private MediaPlayer player;
        private Uri pendingUri;
        private boolean pendingLoop;
        private boolean pendingStart;
        private int pendingSeekMs;
        private Runnable pendingOnStarted;
        private Runnable pendingOnCompletion;
        private int videoWidth;
        private int videoHeight;

        TextureSlot(String name) {
            this.name = name;
            this.view = new TextureView(context);
            this.view.setSurfaceTextureListener(this);
        }

        void prepare(Uri uri, boolean loop, boolean startWhenReady, int seekMs, Runnable onStarted) {
            prepare(uri, loop, startWhenReady, seekMs, onStarted, null);
        }

        void prepare(Uri uri, boolean loop, boolean startWhenReady, int seekMs, Runnable onStarted, Runnable onCompletion) {
            pendingUri = uri;
            pendingLoop = loop;
            pendingStart = startWhenReady;
            pendingSeekMs = seekMs;
            pendingOnStarted = onStarted;
            pendingOnCompletion = onCompletion;
            if (surface != null) startPending();
        }

        void pause() {
            try {
                if (player != null && player.isPlaying()) player.pause();
            } catch (IllegalStateException ignored) {
                android.util.Log.w("StageCorePlayer", name + " pause ignored invalid state");
            }
        }

        boolean isPlaying() {
            try {
                return player != null && player.isPlaying();
            } catch (IllegalStateException ignored) {
                return false;
            }
        }

        void stopAndReset() {
            pendingUri = null;
            pendingOnStarted = null;
            pendingOnCompletion = null;
            videoWidth = 0;
            videoHeight = 0;
            releasePlayer();
        }

        void applyLayout() {
            if (videoWidth > 0 && videoHeight > 0) applyVideoLayout(view, videoWidth, videoHeight);
            else view.setLayoutParams(fillParams());
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            surface = new Surface(surfaceTexture);
            if (pendingUri != null) startPending();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            applyLayout();
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            releasePlayer();
            if (surface != null) {
                surface.release();
                surface = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            // No-op.
        }

        private void startPending() {
            if (surface == null || pendingUri == null) return;
            Uri uri = pendingUri;
            boolean loop = pendingLoop;
            boolean startWhenReady = pendingStart;
            int seekMs = pendingSeekMs;
            Runnable onStarted = pendingOnStarted;
            Runnable onCompletion = pendingOnCompletion;
            releasePlayer();
            MediaPlayer next = new MediaPlayer();
            player = next;
            try {
                next.setSurface(surface);
                next.setLooping(loop);
                next.setDataSource(context, uri);
                next.setOnPreparedListener(mp -> {
                    videoWidth = mp.getVideoWidth();
                    videoHeight = mp.getVideoHeight();
                    applyVideoLayout(view, videoWidth, videoHeight);
                    if (seekMs > 0) mp.seekTo(seekMs);
                    if (startWhenReady) {
                        mp.start();
                        if (onStarted != null) mainHandler.post(onStarted);
                    }
                    android.util.Log.i("StageCorePlayer", name + " prepared uri=" + uri);
                });
                next.setOnCompletionListener(mp -> {
                    if (onCompletion != null) mainHandler.post(onCompletion);
                });
                next.setOnErrorListener((mp, what, extra) -> {
                    handleSlotError(name, what, extra);
                    return true;
                });
                next.prepareAsync();
            } catch (IOException | IllegalArgumentException | IllegalStateException e) {
                handleSlotException(name, e);
            }
        }

        private void releasePlayer() {
            MediaPlayer old = player;
            player = null;
            if (old != null) {
                try {
                    old.setOnPreparedListener(null);
                    old.setOnCompletionListener(null);
                    old.setOnErrorListener(null);
                    old.reset();
                    old.release();
                } catch (IllegalStateException ignored) {
                    old.release();
                }
            }
        }
    }

    private void handleSlotError(String name, int what, int extra) {
        if ("main".equals(name)) mainPlaying = false;
        if ("overlay".equals(name)) {
            currentOverlay = "none";
            overlayVideo.view.setVisibility(View.GONE);
        }
        if ("live".equals(name)) {
            currentLive = "none";
            liveVideo.view.setVisibility(View.GONE);
        }
        showStatus(name + " error: " + what + "/" + extra);
        android.util.Log.e("StageCorePlayer", name + " error what=" + what + " extra=" + extra);
    }

    private void handleSlotException(String name, Exception error) {
        if ("main".equals(name)) mainPlaying = false;
        if ("overlay".equals(name)) currentOverlay = "none";
        if ("live".equals(name)) currentLive = "none";
        showStatus(name + " exception: " + error.getClass().getSimpleName());
        android.util.Log.e("StageCorePlayer", name + " exception", error);
    }
}
