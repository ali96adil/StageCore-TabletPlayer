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

    private TextView statusHeader;
    private TextView actionResult;
    private TextView resultDetails;
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
    private CheckBox keepAwakeCheck;
    private CheckBox heartbeatCheck;

    private long lastTapMs = 0;
    private int cornerTapCount = 0;
    private String lastError = "";
    private String lastAction = "جاهز";
    private String lastActionState = "READY ✅";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        appSettings = AppSettings.load(this);
        applyAwakeFlag();
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
            @Override public String storagePermissionState() { return MainActivity.this.storagePermissionState(); }
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
        showActionResult("Startup", "جاهز للعرض. OSC يعمل على UDP 9000.", "READY ✅", false);
        setControlsVisible(!appSettings.showModeOnLaunch);
        if (appSettings.autoDiscover) startDiscovery(false);
        heartbeatReporter.start();
        applyShowLockSurface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyAwakeFlag();
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
        readinessBadge = badge("جاهزية العرض: جاري الفحص...");
        panel.addView(readinessBadge);

        statusHeader = badge("جاهز");
        statusHeader.setBackgroundColor(0x55222222);
        panel.addView(statusHeader);

        actionResult = badge("آخر أمر: جاهز");
        actionResult.setBackgroundColor(0x5533AA55);
        panel.addView(actionResult);

        panel.addView(help("الخمس نقرات أعلى اليسار تفتح/تخفي الإعدادات. النتائج الطويلة صارت بالأسفل حتى الشاشة ما تقفز."));

        panel.addView(section("اختبار سريع"));
        panel.addView(rowButtons(
                button("Reload + Scan", v -> reloadManifestAndScan()),
                button("Cue Preview", v -> showActionResult("Cue Preview", cuePreviewSummary(), "READY ✅", true)),
                button("Pre-show Check", v -> showActionResult("Pre-show Check", preShowCheckSummary(), preShowCheckSummary().contains("READY") ? "READY ✅" : "CHECK ⚠️", true))
        ));
        panel.addView(rowButtons(
                button("Prepare 1", v -> showResult("Prepare 1", executor.prepareCue(1))),
                button("GO 1", v -> showResult("GO 1", executor.goCue(1))),
                button("Overlay 2", v -> showResult("Overlay 2", executor.goCue(2)))
        ));
        panel.addView(rowButtons(
                button("Sample Live Cue 3", v -> showResult("Sample Live Cue 3", executor.goCue(3))),
                button("Blackout 4", v -> showResult("Blackout 4", executor.goCue(4))),
                button("Clear", v -> showResult("Clear", player.clearBlackout()))
        ));
        panel.addView(rowButtons(
                button("Identify", v -> showResult("Identify", player.identify())),
                button("وضع العرض", v -> enterShowModeNow()),
                button("إغلاق التطبيق", v -> finish())
        ));

        panel.addView(section("اختبار Cue يدوي"));
        cueNumberInput = editText();
        cueNumberInput.setText("1");
        panel.addView(field("رقم Cue", cueNumberInput));
        panel.addView(rowButtons(
                button("Prepare Cue", v -> showResult("Prepare Cue " + selectedCueNumber(), executor.prepareCue(selectedCueNumber()))),
                button("GO Cue", v -> showResult("GO Cue " + selectedCueNumber(), executor.goCue(selectedCueNumber())))
        ));

        panel.addView(section("اختبار Live يدوي"));
        liveUrlInput = editText();
        liveUrlInput.setText("http://192.168.3.80:81/stream");
        panel.addView(field("Live URL", liveUrlInput));
        panel.addView(rowButtons(
                button("Test Live URL", v -> testLiveUrl()),
                button("Hide Live", v -> showResult("Hide Live", player.hideLive()))
        ));
        panel.addView(help("ملاحظة: Sample Live Cue 3 يستخدم رابط المنفست. Test Live URL يستخدم الرابط المكتوب هنا."));

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
                    updateStatusHeader();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                appSettings.save(MainActivity.this);
                showActionResult("Brightness", "تم ضبط السطوع: " + appSettings.brightnessPercent + "%", "READY ✅", false);
                pokeHeartbeat();
            }
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

        panel.addView(section("هوية التابلت"));
        deviceIdInput = editText();
        deviceNameInput = editText();
        panel.addView(field("ID التابلت", deviceIdInput));
        panel.addView(field("اسم الجهاز / اسم الممثل", deviceNameInput));
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
        keepAwakeCheck = checkBox("إبقاء الشاشة شغالة دائماً", appSettings.keepScreenAwake);
        heartbeatCheck = checkBox("إرسال حالة التابلت للسيرفر", appSettings.heartbeatEnabled);
        panel.addView(showModeCheck);
        panel.addView(showLockCheck);
        panel.addView(keepAwakeCheck);
        panel.addView(heartbeatCheck);
        panel.addView(help("Heartbeat: " + appSettings.heartbeatLabel()));
        panel.addView(rowButtons(
                button("إرسال حالة الآن", v -> {
                    pokeHeartbeat();
                    showActionResult("Heartbeat", "تم طلب إرسال حالة الآن.", "READY ✅", false);
                }),
                button("دخول وضع العرض الآن", v -> enterShowModeNow())
        ));

        panel.addView(section("ملفات الفيديو والصلاحيات"));
        panel.addView(help(mediaResolver.mediaFolderHelpArabic()));
        panel.addView(help("المسار المطلوب: /sdcard/TheatreVideos — المنفست يحدد أي Cue يشغل أي ملف."));
        panel.addView(rowButtons(
                button("تجهيز المجلد", v -> showActionResult("Prepare Folder", mediaResolver.prepareFolderSummary(), "READY ✅", true)),
                button("فتح مجلد الفيديوات", v -> openMediaFolder()),
                button("فحص الملفات", v -> showActionResult("Media Scan", compactMediaScanSummary(), compactMediaScanSummary().contains("READY") ? "READY ✅" : "CHECK ⚠️", true))
        ));
        panel.addView(rowButtons(
                button("إعادة تحميل + فحص", v -> reloadManifestAndScan()),
                button("فحص الصلاحيات", v -> showActionResult("Storage Permission", storagePermissionSummary(), storagePermissionState().equals("مفعّل") ? "READY ✅" : "CHECK ⚠️", true)),
                button("فتح صلاحيات التخزين", v -> openStorageSettings())
        ));

        panel.addView(section("النسخة والتحديث"));
        panel.addView(help(buildInfoSummary()));
        panel.addView(help("التحديث داخل التطبيق ممكن لاحقاً إذا وفرنا رابط APK ثابت وموقّع. حالياً الزر يفتح صفحة التحديثات/الأرتيفاكت حتى ننزلها يدويًا بأمان."));
        panel.addView(rowButtons(
                button("فتح صفحة التحديثات", v -> openUpdatePage()),
                button("عرض معلومات النسخة", v -> showActionResult("Build Info", buildInfoSummary(), "READY ✅", true))
        ));

        panel.addView(section("تفاصيل آخر نتيجة"));
        resultDetails = text(13f);
        resultDetails.setPadding(12, 10, 12, 10);
        resultDetails.setBackgroundColor(0x44222222);
        panel.addView(resultDetails);

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
        button.setMinHeight(44);
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
        if (keepAwakeCheck != null) keepAwakeCheck.setChecked(appSettings.keepScreenAwake);
        if (heartbeatCheck != null) heartbeatCheck.setChecked(appSettings.heartbeatEnabled);
        if (brightnessLabel != null) brightnessLabel.setText("السطوع: " + appSettings.brightnessPercent + "%");
        updateReadinessBadge();
        updateStatusHeader();
    }

    private void saveSettingsFromFields() {
        appSettings.deviceId = value(deviceIdInput, appSettings.deviceId);
        appSettings.deviceName = value(deviceNameInput, appSettings.deviceName);
        appSettings.serverHost = value(serverHostInput, "");
        appSettings.serverPort = parsePort(value(serverPortInput, String.valueOf(appSettings.serverPort)), appSettings.serverPort);
        appSettings.autoDiscover = autoDiscoverCheck != null && autoDiscoverCheck.isChecked();
        appSettings.showModeOnLaunch = showModeCheck != null && showModeCheck.isChecked();
        appSettings.showLockEnabled = showLockCheck != null && showLockCheck.isChecked();
        appSettings.keepScreenAwake = keepAwakeCheck == null || keepAwakeCheck.isChecked();
        appSettings.heartbeatEnabled = heartbeatCheck != null && heartbeatCheck.isChecked();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        applyAwakeFlag();
        applyScreenBrightness(appSettings.brightnessPercent);
        applyOrientation(appSettings.orientationMode);
        player.setVideoScaleMode(appSettings.videoScaleMode);
        refreshSettingsFields();
        showActionResult("Save Settings", "تم حفظ الإعدادات.", "READY ✅", false);
        applyShowLockSurface();
        pokeHeartbeat();
    }

    private void regenerateDeviceId() {
        appSettings.deviceId = "tablet-" + java.util.UUID.randomUUID();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        refreshSettingsFields();
        showActionResult("Regenerate ID", "تم توليد ID جديد لهذا التابلت.", "READY ✅", true);
        pokeHeartbeat();
    }

    private void setScale(String scale) {
        appSettings.videoScaleMode = scale;
        appSettings.save(this);
        player.setVideoScaleMode(scale);
        showActionResult("Scale", "تم تغيير حجم الفيديو إلى: " + scale, "READY ✅", false);
        pokeHeartbeat();
    }

    private void setOrientation(String orientation) {
        appSettings.orientationMode = orientation;
        appSettings.save(this);
        applyOrientation(orientation);
        showActionResult("Orientation", "تم تغيير اتجاه العرض إلى: " + orientation, "READY ✅", false);
        pokeHeartbeat();
    }

    private void startDiscovery(boolean visibleFeedback) {
        saveSettingsFromFieldsWithoutRender();
        if (visibleFeedback && discoveryInfo != null) discoveryInfo.setText("جاري البحث عن StageCore داخل الشبكة...");
        showActionResult("Discovery", "جاري البحث عن StageCore داخل الشبكة...", "Running...", false);
        discovery.start(new StageCoreDiscovery.Callback() {
            @Override
            public void onFound(String name, String host, int port, String serviceType) {
                appSettings.serverHost = host;
                appSettings.serverPort = port > 0 ? port : appSettings.serverPort;
                appSettings.save(MainActivity.this);
                refreshSettingsFields();
                String message = "تم العثور على StageCore: " + name + " — " + appSettings.serverLabel() + " — " + serviceType;
                if (discoveryInfo != null) discoveryInfo.setText(message);
                showActionResult("Discovery", message, "READY ✅", true);
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
        showActionResult("Discovery", "تم إيقاف البحث التلقائي.", "READY ✅", false);
    }

    private void reloadManifestAndScan() {
        loadExternalOrSample();
        String details = "تمت إعادة تحميل المنفست.\n\n" + compactMediaScanSummary() + "\n\n" + cuePreviewSummary();
        showActionResult("Reload + Scan", details, details.contains("النواقص: 0") ? "READY ✅" : "CHECK ⚠️", true);
        pokeHeartbeat();
    }

    private void loadExternalOrSample() {
        manifestStore.tryLoadFromDiskOrSample(mediaResolver.manifestFile());
    }

    private void showResult(String actionName, CommandResult result) {
        String message = result.toString();
        String severity = classifyResult(message);
        if (severity.contains("FAILED") || severity.contains("CHECK")) lastError = message;
        showActionResult(actionName, message, severity, false);
        pokeHeartbeat();
    }

    private String classifyResult(String message) {
        if (message == null) return "CHECK ⚠️";
        String upper = message.toUpperCase(java.util.Locale.US);
        if (upper.contains("FAILED") || upper.contains("ERROR") || upper.contains("MISSING") || upper.contains("NOT_FOUND")) return "FAILED ❌";
        if (upper.contains("REJECTED") || upper.contains("CHECK")) return "CHECK ⚠️";
        return "READY ✅";
    }

    private void showActionResult(String actionName, String details, String severity, boolean keepFullDetails) {
        lastAction = actionName;
        lastActionState = severity;
        updateStatusHeader();
        if (actionResult != null) {
            actionResult.setText("آخر أمر: " + actionName + " — " + severity + "\n" + firstLine(details));
            actionResult.setBackgroundColor(severity.contains("FAILED") ? 0x55AA3333 : severity.contains("CHECK") ? 0x55AA8833 : 0x5533AA55);
        }
        if (resultDetails != null) {
            resultDetails.setText((keepFullDetails ? details : compactDetails(details)) + "\n\n" + buildInfoSummary());
        }
        updateReadinessBadge();
    }

    private String firstLine(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String clean = value.trim();
        int nl = clean.indexOf('\n');
        return nl >= 0 ? clean.substring(0, nl) : clean;
    }

    private String compactDetails(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.length() <= 900) return clean;
        return clean.substring(0, 900) + "\n...\nافتح Cue Preview أو Pre-show Check للتفاصيل الكاملة.";
    }

    private void updateStatusHeader() {
        if (statusHeader == null || appSettings == null || stageCoreClient == null) return;
        statusHeader.setText("الجهاز: " + stageCoreClient.deviceName()
                + "\nالسيرفر: " + appSettings.serverLabel()
                + " | Heartbeat: " + appSettings.heartbeatLabel()
                + "\nالصورة: " + appSettings.videoScaleMode
                + " | الاتجاه: " + appSettings.orientationMode
                + " | السطوع: " + appSettings.brightnessPercent + "%"
                + "\nآخر أمر: " + lastAction + " — " + lastActionState);
    }

    private void setControlsVisible(boolean visible) {
        if (controlsPanel != null) controlsPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        player.setStatusVisible(false);
        if (visible) {
            refreshSettingsFields();
            showActionResult("Settings", "لوحة الإعدادات مفتوحة.", "READY ✅", false);
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
        showActionResult("Storage Settings", "فتح صفحة صلاحيات التخزين للنظام.", "READY ✅", false);
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
            showActionResult("Open Folder", "تم طلب فتح مدير الملفات.\nالمجلد المطلوب:\n" + mediaResolver.baseDir().getAbsolutePath(), "READY ✅", true);
        } catch (Exception ignored) {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                startActivity(picker);
                showActionResult("Open Folder", "اختر مجلد TheatreVideos يدويًا.\nالمسار المطلوب:\n" + mediaResolver.baseDir().getAbsolutePath(), "CHECK ⚠️", true);
            } catch (Exception failed) {
                lastError = "File manager unavailable";
                showActionResult("Open Folder", "ما كدرت أفتح مدير الملفات تلقائيًا.\nافتح File Manager يدويًا وروح إلى:\n" + mediaResolver.baseDir().getAbsolutePath(), "FAILED ❌", true);
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
                + "\nKeep awake: " + (appSettings.keepScreenAwake ? "مفعل" : "متوقف")
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
        builder.append("\nملاحظة: رقم الكيو لا يفرض اسم الفيديو. المنفست هو الذي يحدد الملف والسلوك.");
        for (TabletCue cue : manifest.cues) {
            builder.append("\n\nCue ").append(cue.tabletSequence)
                    .append(" — ").append(cue.name)
                    .append(" | StageCore Cue ").append(cue.sourceStageCoreSequence);
            for (TabletAction action : cue.actions) {
                builder.append("\n  ")
                        .append(action.type)
                        .append(" → ")
                        .append(mediaLabel(manifest, action.mediaKey))
                        .append(" | loop=").append(action.loop)
                        .append(" | end=").append(action.endBehavior)
                        .append(" | fadeIn=").append(action.dissolveInMs)
                        .append(" | fadeOut=").append(action.dissolveOutMs);
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
            showActionResult("Test Live URL", "Live URL فارغ. اكتب رابط مثل:\nhttp://192.168.3.80:81/stream", "FAILED ❌", true);
            return;
        }
        showResult("Test Live URL", player.showLive(url));
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

    private void applyAwakeFlag() {
        if (appSettings == null || appSettings.keepScreenAwake || appSettings.showLockEnabled) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
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
        if (appSettings == null) return;
        applyAwakeFlag();
        if (!appSettings.showLockEnabled) return;
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
        appSettings.keepScreenAwake = keepAwakeCheck == null || keepAwakeCheck.isChecked();
        appSettings.heartbeatEnabled = heartbeatCheck == null || heartbeatCheck.isChecked();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        applyAwakeFlag();
    }

    private String buildInfoSummary() {
        return "Version: " + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ")"
                + "\nBuild: " + BuildConfig.BUILD_LABEL
                + "\nPackage: " + getPackageName();
    }

    private void openUpdatePage() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ali96adil/StageCore-TabletPlayer/actions"));
            startActivity(intent);
            showActionResult("Updates", "تم فتح صفحة GitHub Actions للتحديثات. التحديث الداخلي الكامل يحتاج endpoint ثابت للـAPK وتوقيع release.", "CHECK ⚠️", true);
        } catch (Exception error) {
            lastError = "Cannot open update page";
            showActionResult("Updates", "ما كدرت أفتح صفحة التحديثات من هذا الجهاز.", "FAILED ❌", true);
        }
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
