package com.stagecore.player;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

import com.stagecore.player.model.CommandResult;

import java.io.File;

public final class TabletPlayer {
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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

    public TabletPlayer(Context context) {
        this.context = context;
    }

    public void attachTo(FrameLayout stage) {
        mainVideo = new VideoView(context);
        overlayVideo = new VideoView(context);
        liveVideo = new VideoView(context);
        blackoutView = new View(context);
        statusView = new TextView(context);

        mainVideo.setBackgroundColor(Color.BLACK);
        overlayVideo.setBackgroundColor(Color.TRANSPARENT);
        liveVideo.setBackgroundColor(Color.TRANSPARENT);
        overlayVideo.setVisibility(View.GONE);
        liveVideo.setVisibility(View.GONE);

        blackoutView.setBackgroundColor(Color.BLACK);
        blackoutView.setVisibility(View.GONE);

        statusView.setTextColor(Color.WHITE);
        statusView.setBackgroundColor(0x66000000);
        statusView.setPadding(18, 12, 18, 12);
        statusView.setText("StageCore Player ready");

        FrameLayout.LayoutParams fill = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        stage.addView(mainVideo, fill);
        stage.addView(overlayVideo, fill);
        stage.addView(liveVideo, fill);
        stage.addView(blackoutView, fill);

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        stage.addView(statusView, statusParams);
    }

    public CommandResult prepareMain(File file) {
        if (!isReadableFile(file)) return CommandResult.failed("MEDIA_NOT_FOUND", missing(file));
        preparedMainFile = file;
        mainVideo.setVideoURI(Uri.fromFile(file));
        mainVideo.setOnPreparedListener(mp -> mainVideo.seekTo(1));
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
        mainVideo.setVideoURI(Uri.fromFile(file));
        mainVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mainVideo.start();
            mainPlaying = true;
            showStatus("Playing main: " + currentMain);
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
        showStatus("Main stopped");
        return CommandResult.completed("Main stopped");
    }

    public CommandResult playOverlay(File file, int dissolveInMs, int dissolveOutMs) {
        if (!isReadableFile(file)) return CommandResult.failed("MEDIA_NOT_FOUND", missing(file));
        overlayVideo.animate().cancel();
        overlayVideo.setAlpha(0f);
        overlayVideo.setVisibility(View.VISIBLE);
        overlayVideo.setVideoURI(Uri.fromFile(file));
        overlayVideo.setOnPreparedListener(mp -> {
            mp.setLooping(false);
            overlayVideo.start();
            overlayVideo.animate().alpha(1f).setDuration(Math.max(0, dissolveInMs)).start();
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
        overlayVideo.animate().alpha(0f).setDuration(Math.max(0, dissolveOutMs)).withEndAction(() -> {
            overlayVideo.stopPlayback();
            overlayVideo.setVisibility(View.GONE);
            currentOverlay = "none";
            showStatus("Overlay hidden");
        }).start();
        return CommandResult.completed("Overlay hide requested");
    }

    public CommandResult showLive(String url) {
        if (url == null || url.trim().isEmpty()) {
            return CommandResult.failed("LIVE_URL_MISSING", "Live URL is missing");
        }
        liveVideo.setAlpha(1f);
        liveVideo.setVisibility(View.VISIBLE);
        liveVideo.setVideoURI(Uri.parse(url));
        liveVideo.setOnPreparedListener(mp -> liveVideo.start());
        liveVideo.setOnErrorListener((mp, what, extra) -> {
            showStatus("Live error: " + what + "/" + extra);
            return true;
        });
        currentLive = url;
        hideBlackout();
        showStatus("Live: " + url);
        return CommandResult.completed("Live visible");
    }

    public CommandResult hideLive() {
        if (liveVideo != null) {
            liveVideo.stopPlayback();
            liveVideo.setVisibility(View.GONE);
        }
        currentLive = "none";
        showStatus("Live hidden");
        return CommandResult.completed("Live hidden");
    }

    public CommandResult blackout() {
        blackoutVisible = true;
        blackoutView.setVisibility(View.VISIBLE);
        showStatus("BLACKOUT");
        return CommandResult.completed("Blackout visible");
    }

    public CommandResult clearBlackout() {
        hideBlackout();
        showStatus("Blackout cleared");
        return CommandResult.completed("Blackout cleared");
    }

    public CommandResult identify() {
        showStatus("IDENTIFY - StageCore Player");
        statusView.setBackgroundColor(0xCCFFFFFF);
        statusView.setTextColor(Color.BLACK);
        mainHandler.postDelayed(() -> {
            statusView.setBackgroundColor(0x66000000);
            statusView.setTextColor(Color.WHITE);
        }, 900);
        return CommandResult.completed("Identified");
    }

    public void setStatusVisible(boolean visible) {
        if (statusView != null) statusView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public String observationSummary() {
        return "main=" + currentMain
                + " prepared=" + preparedMain
                + " playing=" + mainPlaying
                + " overlay=" + currentOverlay
                + " live=" + currentLive
                + " blackout=" + blackoutVisible;
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
}
