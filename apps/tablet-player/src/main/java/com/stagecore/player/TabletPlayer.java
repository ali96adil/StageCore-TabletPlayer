package com.stagecore.player;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import com.stagecore.player.model.CommandResult;

import java.io.File;
import java.util.Locale;

public final class TabletPlayer {
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private FrameLayout stageView;
    private VideoView mainVideo;
    private VideoView overlayVideo;
    private VideoView liveVideo;
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
    private int mainVideoWidth = 0;
    private int mainVideoHeight = 0;
    private int overlayVideoWidth = 0;
    private int overlayVideoHeight = 0;
    private int liveVideoWidth = 0;
    private int liveVideoHeight = 0;

    public TabletPlayer(Context context) {
        this.context = context;
    }

    public void attachTo(FrameLayout stage) {
        stageView = stage;
        mainVideo = new VideoView(context);
        overlayVideo = new VideoView(context);
        liveVideo = new VideoView(context);
        blackoutView = new View(context);
        statusView = new TextView(context);

        mainVideo.setBackgroundColor(Color.BLACK);
        overlayVideo.setBackgroundColor(Color.TRANSPARENT);
        liveVideo.setBackgroundColor(Color.TRANSPARENT);
        configureSurfaceOrder();
        overlayVideo.setVisibility(View.GONE);
        liveVideo.setVisibility(View.GONE);

        blackoutView.setBackgroundColor(Color.BLACK);
        blackoutView.setVisibility(View.GONE);

        statusView.setTextColor(Color.WHITE);
        statusView.setBackgroundColor(0x66000000);
        statusView.setPadding(18, 12, 18, 12);
        statusView.setText("StageCore Player ready");
        statusView.setVisibility(View.GONE);

        FrameLayout.LayoutParams fill = fillParams();
        stage.addView(mainVideo, fillParams());
        stage.addView(overlayVideo, fillParams());
        stage.addView(liveVideo, fillParams());
        stage.addView(blackoutView, fill);

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
        mainVideo.clearAnimation();
        mainVideo.setVisibility(View.VISIBLE);
        mainVideo.setVideoURI(Uri.fromFile(file));
        mainVideo.setOnPreparedListener(mp -> {
            mainVideoWidth = mp.getVideoWidth();
            mainVideoHeight = mp.getVideoHeight();
            applyVideoLayout(mainVideo, mainVideoWidth, mainVideoHeight);
            mainVideo.seekTo(1);
        });
        preparedMain = file.getName();
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
        mainVideo.clearAnimation();
        mainVideo.setAlpha(1f);
        mainVideo.setVisibility(View.VISIBLE);
        mainVideo.setVideoURI(Uri.fromFile(file));
        mainVideo.setOnPreparedListener(mp -> {
            mainVideoWidth = mp.getVideoWidth();
            mainVideoHeight = mp.getVideoHeight();
            applyVideoLayout(mainVideo, mainVideoWidth, mainVideoHeight);
            mp.setLooping(true);
            mainHandler.post(() -> {
                mainVideo.start();
                mainPlaying = true;
                showStatus("Playing main: " + currentMain);
            });
        });
        mainVideo.setOnErrorListener((mp, what, extra) -> {
            mainPlaying = false;
            showStatus("Main error: " + what + "/" + extra);
            return true;
        });
        currentMain = file.getName();
        preparedMainFile = file;
        preparedMain = file.getName();
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
        if (mainVideo != null) {
            mainVideo.stopPlayback();
        }
        mainPlaying = false;
        currentMain = "none";
        preparedMain = "none";
        preparedMainFile = null;
        mainVideoWidth = 0;
        mainVideoHeight = 0;
        showStatus("Main stopped");
        return CommandResult.completed("Main stopped");
    }

    public CommandResult playOverlay(File file, int dissolveInMs, int dissolveOutMs) {
        if (!isReadableFile(file)) return CommandResult.failed("MEDIA_NOT_FOUND", missing(file));
        overlayVideo.animate().cancel();
        overlayVideo.setAlpha(dissolveInMs <= 0 ? 1f : 0f);
        overlayVideo.setVisibility(View.VISIBLE);
        overlayVideo.bringToFront();
        keepControlsOnTop();
        overlayVideo.setVideoURI(Uri.fromFile(file));
        overlayVideo.setOnPreparedListener(mp -> {
            overlayVideoWidth = mp.getVideoWidth();
            overlayVideoHeight = mp.getVideoHeight();
            applyVideoLayout(overlayVideo, overlayVideoWidth, overlayVideoHeight);
            mp.setLooping(false);
            overlayVideo.start();
            if (dissolveInMs > 0) {
                overlayVideo.animate().alpha(1f).setDuration(dissolveInMs).start();
            }
        });
        overlayVideo.setOnErrorListener((mp, what, extra) -> {
            currentOverlay = "none";
            overlayVideo.setVisibility(View.GONE);
            showStatus("Overlay error: " + what + "/" + extra);
            return true;
        });
        overlayVideo.setOnCompletionListener(mp -> hideOverlay(dissolveOutMs));
        currentOverlay = file.getName();
        hideBlackout();
        showStatus("Overlay: " + currentOverlay);
        return CommandResult.completed("Playing overlay " + file.getName());
    }

    public CommandResult hideOverlay(int dissolveOutMs) {
        if (overlayVideo == null || overlayVideo.getVisibility() != View.VISIBLE) {
            currentOverlay = "none";
            return CommandResult.completed("Overlay already hidden");
        }
        overlayVideo.animate().cancel();
        if (dissolveOutMs <= 0) {
            overlayVideo.stopPlayback();
            overlayVideo.setVisibility(View.GONE);
            currentOverlay = "none";
            overlayVideoWidth = 0;
            overlayVideoHeight = 0;
            showStatus("Overlay hidden");
            return CommandResult.completed("Overlay hidden");
        }
        overlayVideo.animate().alpha(0f).setDuration(dissolveOutMs).withEndAction(() -> {
            overlayVideo.stopPlayback();
            overlayVideo.setVisibility(View.GONE);
            currentOverlay = "none";
            overlayVideoWidth = 0;
            overlayVideoHeight = 0;
            showStatus("Overlay hidden");
        }).start();
        return CommandResult.completed("Overlay hide requested");
    }

    public CommandResult showLive(String url) {
        if (url == null || url.trim().isEmpty()) {
            return CommandResult.failed("LIVE_URL_MISSING", "Live URL is missing");
        }
        hideBlackout();
        hideOverlay(0);
        liveVideo.setAlpha(1f);
        liveVideo.setVisibility(View.VISIBLE);
        liveVideo.bringToFront();
        keepControlsOnTop();
        liveVideo.setVideoURI(Uri.parse(url));
        liveVideo.setOnPreparedListener(mp -> {
            liveVideoWidth = mp.getVideoWidth();
            liveVideoHeight = mp.getVideoHeight();
            applyVideoLayout(liveVideo, liveVideoWidth, liveVideoHeight);
            liveVideo.start();
        });
        liveVideo.setOnErrorListener((mp, what, extra) -> {
            currentLive = "none";
            liveVideo.setVisibility(View.GONE);
            showStatus("Live error: " + what + "/" + extra);
            return true;
        });
        currentLive = url;
        showStatus("Live: " + url);
        return CommandResult.completed("Live visible");
    }

    public CommandResult hideLive() {
        if (liveVideo != null) {
            liveVideo.stopPlayback();
            liveVideo.setVisibility(View.GONE);
        }
        currentLive = "none";
        liveVideoWidth = 0;
        liveVideoHeight = 0;
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

    private void configureSurfaceOrder() {
        configureSurface(mainVideo, false);
        configureSurface(overlayVideo, true);
        configureSurface(liveVideo, true);
    }

    private void configureSurface(VideoView video, boolean mediaOverlay) {
        if (video instanceof SurfaceView) {
            ((SurfaceView) video).setZOrderMediaOverlay(mediaOverlay);
        }
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
        applyOrReset(mainVideo, mainVideoWidth, mainVideoHeight);
        applyOrReset(overlayVideo, overlayVideoWidth, overlayVideoHeight);
        applyOrReset(liveVideo, liveVideoWidth, liveVideoHeight);
    }

    private void applyOrReset(VideoView video, int videoWidth, int videoHeight) {
        if (video == null) return;
        if (videoWidth > 0 && videoHeight > 0) {
            applyVideoLayout(video, videoWidth, videoHeight);
        } else {
            resetVideoLayout(video);
        }
    }

    private void resetVideoLayout(VideoView video) {
        if (video == null) return;
        video.setLayoutParams(fillParams());
    }

    private void applyVideoLayout(VideoView video, int videoWidth, int videoHeight) {
        if (video == null || stageView == null) return;
        int stageWidth = stageView.getWidth();
        int stageHeight = stageView.getHeight();
        if (stageWidth <= 0 || stageHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            resetVideoLayout(video);
            return;
        }
        if (AppSettings.SCALE_FULL.equals(videoScaleMode)) {
            resetVideoLayout(video);
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
                "video scale mode=%s stage=%dx%d video=%dx%d target=%dx%d",
                videoScaleMode, stageWidth, stageHeight, videoWidth, videoHeight, targetWidth, targetHeight));
    }
}
