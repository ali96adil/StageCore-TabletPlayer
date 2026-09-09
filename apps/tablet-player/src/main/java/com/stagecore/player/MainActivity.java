package com.stagecore.player;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.stagecore.player.model.CommandResult;
import com.stagecore.player.model.MediaItemRef;
import com.stagecore.player.model.TabletAction;
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

public final class MainActivity extends Activity {
    private TabletPlayer player;
    private ManifestStore manifestStore;
    private ManifestExecutor executor;
    private LegacyOscServer oscServer;
    private StageCoreClient stageCoreClient;
    private StageCoreDiscovery discovery;
    private TabletHeartbeatReporter heartbeatReporter;
    private MediaResolver mediaResolver;
    private AppSettings appSettings;

    private TextView info;
    private TextView discoveryInfo;
    private TextView brightnessLabel;
    private TextView readinessBadge;
    private View controlsPanel;
    private EditText deviceIdInput;
    private EditText deviceNameInput;
    private EditText serverHostInput;
    private EditText serverPortInput;
    private EditText cueNumberInput;
    private EditText liveUrlInput;
    private CheckBox autoDiscoverCheck;
    private CheckBox showModeCheck;
    private CheckBox showLockCheck;
    private CheckBox heartbeatCheck;

    private long lastTapMs = 0;
    private int cornerTapCount = 0;
    private String lastError = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        appSettings = AppSettings.load(this);
        applyOrientation(appSettings.orientationMode);
        applyScreenBrightness(appSettings.brightnessPercent);

        player = new TabletPlayer(this);
        manifestStore = new ManifestStore();
        mediaResolver = new MediaResolver();
        mediaResolver.ensureBaseDir();
        executor = new ManifestExecutor(manifestStore, mediaResolver, player);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        discovery = new StageCoreDiscovery(this);
        heartbeatReporter = new TabletHeartbeatReporter(this, new TabletHeartbeatReporter.SnapshotProvider() {
            @Override public AppSettings settings() { return appSettings; }
            @Override public TabletManifest manifest() { return manifestStore.activeManifest(); }
            @Override public String manifestSource() { return manifestStore.activeSource(); }
            @Override public String mediaScanSummary() { return mediaResolver.scanSummary(manifestStore.activeManifest()).replace('\n', ';'); }
            @Override public String storagePermissionState() { return storagePermissionState(); }
            @Override public String playerState() { return player.observationSummary(); }
            @Override public String appMode() { return controlsVisible() ? "SETTINGS" : "SHOW"; }
            @Override public String lastError() { return lastError; }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        player.attachTo(root);
        player.setVideoScaleMode(appSettings.videoScaleMode);
        addControls(root);
        addHotCorner(root);
        setContentView(root);

        loadExternalOrSample();
        oscServer = new LegacyOscServer(executor, player);
        oscServer.start(9000);
        refreshSettingsFields();
        renderInfo("جاهز للعرض. تحكم OSC يعمل على UDP 9000.");
        setControlsVisible(!appSettings.showModeOnLaunch);
        if (appSettings.autoDiscover) startDiscovery(false);
        heartbeatReporter.start();
        applyShowLockSurface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyShowLockSurface();
        if (heartbeatReporter != null) heartbeatReporter.pokeSoon();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyShowLockSurface();
    }

    @Override
    public void onBackPressed() {
        if (appSettings != null && appSettings.showLockEnabled) {
            if (controlsVisible()) setControlsVisible(false);
            applyShowLockSurface();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (appSettings != null && appSettings.showLockEnabled) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (controlsVisible()) setControlsVisible(false);
                applyShowLockSurface();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (heartbeatReporter != null) heartbeatReporter.stop();
        if (oscServer != null) oscServer.stop();
        if (discovery != null) discovery.stop();
        super.onDestroy();
    }

    private void addControls(FrameLayout root) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 14, 18, 14);
        panel.setBackgroundColor(0xDD000000);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panel.setTextDirection(View.TEXT_DIRECTION_RTL);

        panel.addView(title("إعدادات StageCore Player"));
        panel.addView(help("وضع العرض يبقى نظيف ومقفول. افتح/اخفِ الإعدادات بخمس ضغطات سريعة أعلى اليسار."));
        readinessBadge = badge("جاهزية العرض: جاري الفحص...");
        panel.addView(readinessBadge);

        info = text(13f);
        panel.addView(info);

        panel.addView(section("هوية التابلت"));
        deviceIdInput = editText();
        deviceNameInput = editText();
        panel.addView(field("ID التابلت", deviceIdInput));
        panel.addView(field("اسم الجهاز", deviceNameInput));
        panel.addView(rowButtons(
                button("حفظ الإعدادات", v -> saveSettingsFromFields()),
                button("توليد ID جديد", v -> regenerateDeviceId())
        ));

        panel.addView(section("سيرفر StageCore"));
        serverHostInput = editText();
        serverPortInput = editText();
        panel.addView(field("عنوان السيرفر", serverHostInput));
        panel.addView(field("البورت", serverPortInput));
        autoDiscoverCheck = checkBox("اكتشاف تلقائي Bonjour", true);
        panel.addView(autoDiscoverCheck);
        discoveryInfo = help("الاكتشاف يبحث عن _stagecore._tcp و _stagecore-hub._tcp داخل نفس الشبكة.");
        panel.addView(discoveryInfo);
        panel.addView(rowButtons(
                button("بحث تلقائي", v -> startDiscovery(true)),
                button("إيقاف البحث", v -> stopDiscovery())
        ));

        panel.addView(section("الأمان والحالة"));
        showModeCheck = checkBox("فتح التطبيق بوضع العرض النظيف", appSettings.showModeOnLaunch);
        showLockCheck = checkBox("قفل العرض Show Lock", appSettings.showLockEnabled);
        heartbeatCheck = checkBox("إرسال حالة التابلت للسيرفر", appSettings.heartbeatEnabled);
        panel.addView(showModeCheck);
        panel.addView(showLockCheck);
        panel.addView(heartbeatCheck);
        panel.addView(help("Heartbeat: " + appSettings.heartbeatLabel()));
        panel.addView(rowButtons(
                button("فحص قبل العرض", v -> renderInfo(preShowCheckSummary())),
                button("إرسال حالة الآن", v -> pokeHeartbeat()),
                button("دخول وضع العرض الآن", v -> enterShowModeNow())
        ));

        panel.addView(section("الصورة والسطوع"));
        brightnessLabel = help("السطوع: " + appSettings.brightnessPercent + "%");
        panel.addView(brightnessLabel);
        SeekBar brightness = new SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(appSettings.brightnessPercent);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.max(5, progress);
                if (fromUser) {
                    appSettings.brightnessPercent = value;
                    applyScreenBrightness(value);
                    brightnessLabel.setText("السطوع: " + value + "%");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { appSettings.save(MainActivity.this); pokeHeartbeat(); }
        });
        panel.addView(brightness);
        panel.addView(rowButtons(
                button("Full / ملء", v -> setScale(AppSettings.SCALE_FULL)),
                button("Fit / احتواء", v -> setScale(AppSettings.SCALE_FIT)),
                button("Crop / قص", v -> setScale(AppSettings.SCALE_CROP))
        ));
        panel.addView(rowButtons(
                button("اتجاه تلقائي", v -> setOrientation(AppSettings.ORIENTATION_AUTO)),
                button("عمودي", v -> setOrientation(AppSettings.ORIENTATION_PORTRAIT)),
                button("أفقي", v -> setOrientation(AppSettings.ORIENTATION_LANDSCAPE))
        ));

        panel.addView(section("ملفات الفيديو والصلاحيات"));
        panel.addView(help(mediaResolver.mediaFolderHelpArabic()));
        panel.addView(help("ترتيب الكيو مستقل عن أسماء الملفات: ممكن Cue 1 يشغل main_02 إذا المنفست يطلب هذا."));
        panel.addView(rowButtons(
                button("تجهيز المجلد", v -> renderInfo(mediaResolver.prepareFolderSummary())),
                button("فتح مجلد الفيديوات", v -> openMediaFolder()),
                button("فحص الملفات", v -> renderInfo(compactMediaScanSummary()))
        ));
        panel.addView(rowButtons(
                button("إعادة تحميل + فحص", v -> reloadManifestAndScan()),
                button("فحص الصلاحيات", v -> renderInfo(storagePermissionSummary())),
                button("فتح صلاحيات التخزين", v -> openStorageSettings())
        ));
        panel.addView(rowButtons(
                button("عرض Cue Preview", v -> renderInfo(cuePreviewSummary()))
        ));

        panel.addView(section("اختبار Cue يدوي"));
        cueNumberInput = editText();
        cueNumberInput.setText("1");
        panel.addView(field("رقم Cue", cueNumberInput));
        panel.addView(rowButtons(
                button("Prepare Cue", v -> showResult(executor.prepareCue(selectedCueNumber()))),
                button("GO Cue", v -> showResult(executor.goCue(selectedCueNumber())))
        ));

        panel.addView(section("اختبار Live يدوي"));
        liveUrlInput = editText();
        liveUrlInput.setText("http://192.168.3.80:81/stream");
        panel.addView(field("Live URL", liveUrlInput));
        panel.addView(rowButtons(
                button("Test Live", v -> testLiveUrl()),
                button("Hide Live", v -> showResult(player.hideLive()))
        ));

        panel.addView(section("اختبار سريع"));
        panel.addView(rowButtons(
                button("Prepare 1", v -> showResult(executor.prepareCue(1))),
                button("GO 1", v -> showResult(executor.goCue(1))),
                button("Overlay 2", v -> showResult(executor.goCue(2)))
        ));
        panel.addView(rowButtons(
                button("Live 3", v -> showResult(executor.goCue(3))),
                button("Blackout 4", v -> showResult(executor.goCue(4))),
                button("Clear", v -> showResult(player.clearBlackout())),
                button("Identify", v -> showResult(player.identify())),
                button("وضع العرض", v -> setControlsVisible(false)),
                button("إغلاق التطبيق", v -> finish())
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        controlsPanel = scroll;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(scroll, params);
    }

    private void addHotCorner(FrameLayout root) {
        View hotCorner = new View(this);
        hotCorner.setBackgroundColor(Color.TRANSPARENT);
        hotCorner.setOnTouchListener(this::handleCornerTap);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(180, 180, Gravity.TOP | Gravity.START);
        root.addView(hotCorner, params);
    }

    private TextView title(String label) {
        TextView view = text(20f);
        view.setText(label);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(0, 0, 0, 8);
        return view;
    }

    private TextView section(String label) {
        TextView view = text(16f);
        view.setText("\n" + label);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private TextView help(String label) {
        TextView view = text(13f);
        view.setText(label);
        view.setTextColor(0xFFE0E0E0);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private TextView badge(String label) {
        TextView view = text(15f);
        view.setText(label);
        view.setGravity(Gravity.RIGHT);
        view.setPadding(12, 10, 12, 10);
        view.setBackgroundColor(0x44222222);
        return view;
    }

    private TextView text(float size) {
        TextView view = new TextView(this);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        return view;
    }

    private EditText editText() {
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(0xFFAAAAAA);
        editText.setTextDirection(View.TEXT_DIRECTION_LTR);
        editText.setBackgroundColor(0x33222222);
        editText.setPadding(12, 8, 12, 8);
        return editText;
    }

    private LinearLayout field(String label, EditText input) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 5, 0, 5);
        box.addView(help(label));
        box.addView(input);
        return box;
    }

    private CheckBox checkBox(String label, boolean checked) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(Color.WHITE);
        box.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.setChecked(checked);
        return box;
    }

    private LinearLayout rowButtons(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.RIGHT);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (Button button : buttons) row.addView(button);
        return row;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private void refreshSettingsFields() {
        if (deviceIdInput != null) deviceIdInput.setText(appSettings.deviceId);
        if (deviceNameInput != null) deviceNameInput.setText(appSettings.deviceName);
        if (serverHostInput != null) serverHostInput.setText(appSettings.serverHost);
        if (serverPortInput != null) serverPortInput.setText(String.valueOf(appSettings.serverPort));
        if (autoDiscoverCheck != null) autoDiscoverCheck.setChecked(appSettings.autoDiscover);
        if (showModeCheck != null) showModeCheck.setChecked(appSettings.showModeOnLaunch);
        if (showLockCheck != null) showLockCheck.setChecked(appSettings.showLockEnabled);
        if (heartbeatCheck != null) heartbeatCheck.setChecked(appSettings.heartbeatEnabled);
        if (brightnessLabel != null) brightnessLabel.setText("السطوع: " + appSettings.brightnessPercent + "%");
        updateReadinessBadge();
    }

    private void saveSettingsFromFields() {
        appSettings.deviceId = value(deviceIdInput, appSettings.deviceId);
        appSettings.deviceName = value(deviceNameInput, appSettings.deviceName);
        appSettings.serverHost = value(serverHostInput, "");
        appSettings.serverPort = parsePort(value(serverPortInput, String.valueOf(appSettings.serverPort)), appSettings.serverPort);
        appSettings.autoDiscover = autoDiscoverCheck != null && autoDiscoverCheck.isChecked();
        appSettings.showModeOnLaunch = showModeCheck != null && showModeCheck.isChecked();
        appSettings.showLockEnabled = showLockCheck != null && showLockCheck.isChecked();
        appSettings.heartbeatEnabled = heartbeatCheck != null && heartbeatCheck.isChecked();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        applyScreenBrightness(appSettings.brightnessPercent);
        applyOrientation(appSettings.orientationMode);
        player.setVideoScaleMode(appSettings.videoScaleMode);
        refreshSettingsFields();
        renderInfo("تم حفظ الإعدادات.");
        applyShowLockSurface();
        pokeHeartbeat();
    }

    private void regenerateDeviceId() {
        appSettings.deviceId = "tablet-" + java.util.UUID.randomUUID();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        refreshSettingsFields();
        renderInfo("تم توليد ID جديد لهذا التابلت.");
        pokeHeartbeat();
    }

    private void setScale(String scale) {
        appSettings.videoScaleMode = scale;
        appSettings.save(this);
        player.setVideoScaleMode(scale);
        renderInfo("تم تغيير حجم الفيديو إلى: " + scale);
        pokeHeartbeat();
    }

    private void setOrientation(String orientation) {
        appSettings.orientationMode = orientation;
        appSettings.save(this);
        applyOrientation(orientation);
        renderInfo("تم تغيير اتجاه العرض إلى: " + orientation);
        pokeHeartbeat();
    }

    private void startDiscovery(boolean visibleFeedback) {
        saveSettingsFromFieldsWithoutRender();
        if (visibleFeedback && discoveryInfo != null) discoveryInfo.setText("جاري البحث عن StageCore داخل الشبكة...");
        discovery.start(new StageCoreDiscovery.Callback() {
            @Override
            public void onFound(String name, String host, int port, String serviceType) {
                appSettings.serverHost = host;
                appSettings.serverPort = port > 0 ? port : appSettings.serverPort;
                appSettings.save(MainActivity.this);
                refreshSettingsFields();
                String message = "تم العثور على StageCore: " + name + " — " + appSettings.serverLabel() + " — " + serviceType;
                if (discoveryInfo != null) discoveryInfo.setText(message);
                renderInfo(message);
                pokeHeartbeat();
            }

            @Override
            public void onStatus(String message) {
                if (discoveryInfo != null) discoveryInfo.setText(message);
                android.util.Log.i("StageCoreDiscovery", message);
            }
        });
    }

    private void stopDiscovery() {
        if (discovery != null) discovery.stop();
        if (discoveryInfo != null) discoveryInfo.setText("تم إيقاف البحث التلقائي.");
    }

    private void reloadManifest() {
        loadExternalOrSample();
        renderInfo("تمت إعادة تحميل المنفست.");
        pokeHeartbeat();
    }

    private void reloadManifestAndScan() {
        loadExternalOrSample();
        renderInfo("تمت إعادة تحميل المنفست.\n\n" + compactMediaScanSummary() + "\n\n" + cuePreviewSummary());
        pokeHeartbeat();
    }

    private void loadExternalOrSample() {
        manifestStore.tryLoadFromDiskOrSample(mediaResolver.manifestFile());
    }

    private void showResult(CommandResult result) {
        String message = result.toString();
        if (message.contains("FAILED") || message.contains("MEDIA_NOT_FOUND") || message.contains("ERROR")) lastError = message;
        renderInfo(message);
        pokeHeartbeat();
    }

    private void renderInfo(String message) {
        if (info == null) return;
        TabletManifest manifest = manifestStore.activeManifest();
        StringBuilder cues = new StringBuilder();
        for (TabletCue cue : manifest.cues) {
            cues.append("\nكيو تابلت ").append(cue.tabletSequence)
                    .append(" = StageCore Cue ").append(cue.sourceStageCoreSequence)
                    .append(" | ").append(cue.name);
        }
        info.setText(message
                + "\nالجهاز: " + stageCoreClient.deviceName()
                + "\nID: " + stageCoreClient.deviceId()
                + "\nالسيرفر: " + appSettings.serverLabel()
                + "\nHeartbeat: " + appSettings.heartbeatLabel()
                + "\nمصدر المنفست: " + manifestStore.activeSource()
                + "\nالمشروع: " + manifest.showName
                + "\nالصورة: " + appSettings.videoScaleMode
                + " | الاتجاه: " + appSettings.orientationMode
                + " | السطوع: " + appSettings.brightnessPercent + "%"
                + "\nقفل العرض: " + (appSettings.showLockEnabled ? "مفعل" : "متوقف")
                + cues);
        updateReadinessBadge();
    }

    private void setControlsVisible(boolean visible) {
        if (controlsPanel != null) controlsPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        player.setStatusVisible(false);
        if (visible) {
            refreshSettingsFields();
            renderInfo("لوحة الإعدادات مفتوحة.");
        }
        applyShowLockSurface();
        pokeHeartbeat();
    }

    private boolean controlsVisible() {
        return controlsPanel != null && controlsPanel.getVisibility() == View.VISIBLE;
    }

    private boolean handleCornerTap(View view, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        long now = System.currentTimeMillis();
        if (now - lastTapMs > 1200) cornerTapCount = 0;
        lastTapMs = now;
        cornerTapCount++;
        if (cornerTapCount >= 5) {
            cornerTapCount = 0;
            setControlsVisible(!controlsVisible());
        }
        return true;
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

    private void openMediaFolder() {
        mediaResolver.ensureBaseDir();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("content://com.android.externalstorage.documents/root/primary"), "vnd.android.document/root");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            startActivity(intent);
            renderInfo("تم طلب فتح مدير الملفات.\nالمجلد المطلوب:\n" + mediaResolver.baseDir().getAbsolutePath());
        } catch (Exception ignored) {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                startActivity(picker);
                renderInfo("اختر مجلد TheatreVideos يدويًا.\nالمسار المطلوب:\n" + mediaResolver.baseDir().getAbsolutePath());
            } catch (Exception failed) {
                lastError = "File manager unavailable";
                renderInfo("ما كدرت أفتح مدير الملفات تلقائيًا.\nافتح File Manager يدويًا وروح إلى:\n" + mediaResolver.baseDir().getAbsolutePath());
            }
        }
    }

    private String storagePermissionSummary() {
        return "فحص الصلاحيات"
                + "\nAll files access: " + storagePermissionState()
                + "\nمجلد الفيديوات: " + (mediaResolver.baseDir().exists() ? "موجود" : "غير موجود")
                + "\nإذا الملفات ما تنقرأ، افتح صلاحيات التخزين وفعّل Allow management of all files.";
    }

    private String storagePermissionState() {
        boolean allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        return allFiles ? "مفعّل" : "غير مفعّل";
    }

    private String preShowCheckSummary() {
        String scan = compactMediaScanSummary();
        boolean hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        boolean missing = !scan.contains("النواقص: 0");
        String status = hasPermission && !missing ? "READY ✅" : "CHECK NEEDED ⚠️";
        return "فحص قبل العرض: " + status
                + "\nالصلاحيات: " + storagePermissionState()
                + "\nالسيرفر: " + appSettings.serverLabel()
                + "\nHeartbeat: " + appSettings.heartbeatLabel()
                + "\n\n" + scan
                + "\n\n" + cuePreviewSummary();
    }

    private String compactMediaScanSummary() {
        String scan = mediaResolver.scanSummary(manifestStore.activeManifest());
        if (scan.contains("النواقص: 0")) {
            return "READY ✅\n" + scan;
        }
        return "MISSING / CHECK ⚠️\n" + scan;
    }

    private String cuePreviewSummary() {
        TabletManifest manifest = manifestStore.activeManifest();
        if (manifest == null) return "Cue Preview: لا يوجد manifest.";
        StringBuilder builder = new StringBuilder("Cue Preview");
        builder.append("\nملاحظة: رقم الكيو لا يفرض اسم الفيديو. المنفست هو الذي يحدد الملف.");
        for (TabletCue cue : manifest.cues) {
            builder.append("\nCue ").append(cue.tabletSequence)
                    .append(" — ").append(cue.name);
            for (TabletAction action : cue.actions) {
                builder.append("\n  ")
                        .append(action.type)
                        .append(" → ")
                        .append(mediaLabel(manifest, action.mediaKey));
            }
        }
        return builder.toString();
    }

    private String mediaLabel(TabletManifest manifest, String mediaKey) {
        if (mediaKey == null || mediaKey.trim().isEmpty()) return "no media";
        MediaItemRef item = manifest.media.get(mediaKey);
        if (item == null) return mediaKey + " = MISSING KEY";
        if (item.file != null && !item.file.trim().isEmpty()) return mediaKey + " = " + item.file;
        if (item.url != null && !item.url.trim().isEmpty()) return mediaKey + " = " + item.url;
        return mediaKey + " = empty";
    }

    private int selectedCueNumber() {
        return parsePort(value(cueNumberInput, "1"), 1);
    }

    private void testLiveUrl() {
        String url = value(liveUrlInput, "");
        if (url.trim().isEmpty()) {
            lastError = "Live URL missing";
            renderInfo("Live URL فارغ. اكتب رابط مثل:\nhttp://192.168.3.80:81/stream");
            return;
        }
        showResult(player.showLive(url));
    }

    private void enterShowModeNow() {
        saveSettingsFromFieldsWithoutRender();
        setControlsVisible(false);
        applyShowLockSurface();
        pokeHeartbeat();
    }

    private void updateReadinessBadge() {
        if (readinessBadge == null || mediaResolver == null || manifestStore == null || appSettings == null) return;
        String scan = mediaResolver.scanSummary(manifestStore.activeManifest());
        boolean hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        boolean ready = hasPermission && scan.contains("النواقص: 0");
        readinessBadge.setText(ready ? "جاهزية العرض: READY ✅" : "جاهزية العرض: تحتاج فحص ⚠️");
        readinessBadge.setBackgroundColor(ready ? 0x5533AA55 : 0x55AA8833);
    }

    private void applyScreenBrightness(int percent) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = Math.max(0.05f, Math.min(1.0f, percent / 100f));
        getWindow().setAttributes(params);
    }

    private void applyOrientation(String orientation) {
        if (AppSettings.ORIENTATION_LANDSCAPE.equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (AppSettings.ORIENTATION_PORTRAIT.equals(orientation)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    private void applyShowLockSurface() {
        if (appSettings == null || !appSettings.showLockEnabled) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void pokeHeartbeat() {
        if (heartbeatReporter != null) heartbeatReporter.pokeSoon();
    }

    private void saveSettingsFromFieldsWithoutRender() {
        appSettings.deviceId = value(deviceIdInput, appSettings.deviceId);
        appSettings.deviceName = value(deviceNameInput, appSettings.deviceName);
        appSettings.serverHost = value(serverHostInput, appSettings.serverHost);
        appSettings.serverPort = parsePort(value(serverPortInput, String.valueOf(appSettings.serverPort)), appSettings.serverPort);
        appSettings.autoDiscover = autoDiscoverCheck == null || autoDiscoverCheck.isChecked();
        appSettings.showModeOnLaunch = showModeCheck == null || showModeCheck.isChecked();
        appSettings.showLockEnabled = showLockCheck == null || showLockCheck.isChecked();
        appSettings.heartbeatEnabled = heartbeatCheck == null || heartbeatCheck.isChecked();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
    }

    private static String value(EditText editText, String fallback) {
        if (editText == null || editText.getText() == null) return fallback;
        String value = editText.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private static int parsePort(String value, int fallback) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
            // Keep previous valid port.
        }
        return fallback;
    }
}
