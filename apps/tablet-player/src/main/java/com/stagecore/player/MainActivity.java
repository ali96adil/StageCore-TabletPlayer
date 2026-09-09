package com.stagecore.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

import java.util.UUID;

public final class MainActivity extends Activity {
    private TabletPlayer player;
    private ManifestStore manifestStore;
    private ManifestExecutor executor;
    private LegacyOscServer oscServer;
    private StageCoreClient stageCoreClient;
    private TextView info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        player = new TabletPlayer(this);
        manifestStore = new ManifestStore();
        MediaResolver mediaResolver = new MediaResolver();
        executor = new ManifestExecutor(manifestStore, mediaResolver, player);
        stageCoreClient = new StageCoreClient(loadOrCreateDeviceId());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        player.attachTo(root);
        addControls(root, mediaResolver);
        setContentView(root);

        manifestStore.loadBundledSample();
        oscServer = new LegacyOscServer(executor, player);
        oscServer.start(9000);
        renderInfo("Loaded sample manifest. OSC listening on UDP 9000.");
    }

    @Override
    protected void onDestroy() {
        if (oscServer != null) oscServer.stop();
        super.onDestroy();
    }

    private void addControls(FrameLayout root, MediaResolver mediaResolver) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 14, 18, 14);
        panel.setBackgroundColor(0x99000000);

        info = new TextView(this);
        info.setTextColor(Color.WHITE);
        info.setTextSize(13f);
        panel.addView(info);

        LinearLayout row1 = row();
        row1.addView(button("Load sample", v -> {
            manifestStore.loadBundledSample();
            renderInfo("Sample manifest loaded.");
        }));
        row1.addView(button("Prepare cue 1", v -> showResult(executor.prepareCue(1))));
        row1.addView(button("GO cue 1", v -> showResult(executor.goCue(1))));
        row1.addView(button("GO cue 2 overlay", v -> showResult(executor.goCue(2))));
        panel.addView(row1);

        LinearLayout row2 = row();
        row2.addView(button("GO cue 3 live", v -> showResult(executor.goCue(3))));
        row2.addView(button("Hide live", v -> showResult(player.hideLive())));
        row2.addView(button("Blackout", v -> showResult(player.blackout())));
        row2.addView(button("Clear blackout", v -> showResult(player.clearBlackout())));
        row2.addView(button("Identify", v -> showResult(player.identify())));
        panel.addView(row2);

        LinearLayout row3 = row();
        row3.addView(button("Storage settings", v -> openStorageSettings()));
        row3.addView(button("Media folder", v -> renderInfo("Put files here: " + mediaResolver.baseDir().getAbsolutePath())));
        panel.addView(row3);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(scroll, params);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        return row;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private void showResult(CommandResult result) {
        renderInfo(result.toString());
    }

    private void renderInfo(String message) {
        TabletManifest manifest = manifestStore.activeManifest();
        StringBuilder cues = new StringBuilder();
        for (TabletCue cue : manifest.cues) {
            cues.append("\nTablet Cue ").append(cue.tabletSequence)
                    .append(" -> StageCore Cue ").append(cue.sourceStageCoreSequence)
                    .append(" | ").append(cue.name);
        }
        info.setText(message
                + "\nDevice: " + stageCoreClient.deviceId()
                + "\n" + stageCoreClient.hello(manifest)
                + "\nMedia folder: " + new MediaResolver().baseDir().getAbsolutePath()
                + cues);
    }

    private String loadOrCreateDeviceId() {
        SharedPreferences prefs = getSharedPreferences("stagecore-player", MODE_PRIVATE);
        String id = prefs.getString("device_id", null);
        if (id == null) {
            id = "tablet-" + UUID.randomUUID();
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    private void openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}
