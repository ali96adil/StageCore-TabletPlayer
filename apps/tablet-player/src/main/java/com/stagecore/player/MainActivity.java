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
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
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
import com.stagecore.player.model.TabletCue;
import com.stagecore.player.model.TabletManifest;

public final class MainActivity extends Activity {
    private TabletPlayer player;
    private ManifestStore manifestStore;
    private ManifestExecutor executor;
    private LegacyOscServer oscServer;
    private StageCoreClient stageCoreClient;
    private StageCoreDiscovery discovery;
    private MediaResolver mediaResolver;
    private AppSettings appSettings;

    private TextView info;
    private TextView discoveryInfo;
    private TextView brightnessLabel;
    private View controlsPanel;
    private EditText deviceIdInput;
    private EditText deviceNameInput;
    private EditText serverHostInput;
    private EditText serverPortInput;
    private CheckBox autoDiscoverCheck;
    private CheckBox showModeCheck;

    private long lastTapMs = 0;
    private int cornerTapCount = 0;

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
        hideSystemUi();
        if (appSettings.autoDiscover) startDiscovery(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onBackPressed() {
        if (controlsVisible()) {
            setControlsVisible(false);
        } else if (player != null) {
            player.identify();
        }
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
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
        panel.addView(help("وضع العرض مقفول ونظيف: الشاشة تبقى شغالة، أزرار النظام مخفية، والرجوع لا يخرج من التطبيق. افتح/اخفِ الإعدادات بخمس ضغطات سريعة أعلى اليسار."));

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
            @Override public void onStopTrackingTouch(SeekBar seekBar) { appSettings.save(MainActivity.this); }
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
        showModeCheck = checkBox("فتح التطبيق بوضع العرض النظيف", appSettings.showModeOnLaunch);
        panel.addView(showModeCheck);

        panel.addView(section("ملفات الفيديو والصلاحيات"));
        panel.addView(help(mediaResolver.mediaFolderHelpArabic()));
        panel.addView(rowButtons(
                button("تجهيز مجلد الفيديوات", v -> renderInfo(mediaResolver.prepareFolderSummary())),
                button("فحص ملفات الفيديو", v -> renderInfo(mediaResolver.scanSummary(manifestStore.activeManifest())))
        ));
        panel.addView(rowButtons(
                button("فحص الصلاحيات", v -> renderInfo(storagePermissionSummary())),
                button("فتح صلاحيات التخزين", v -> openStorageSettings()),
                button("إعادة تحميل المنفست", v -> reloadManifest())
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
                button("Identify", v -> showResult(player.identify()))
        ));

        panel.addView(section("قفل العرض والخروج"));
        panel.addView(help("الخروج متاح فقط من هنا بعد فتح الإعدادات. زر الرجوع لا يطلع من التطبيق أثناء العرض. زر الطاقة الفيزيائي يبقى تابع للنظام إلا إذا فعلت Screen Pinning / Lock Task من إعدادات أندرويد."));
        panel.addView(rowButtons(
                button("وضع العرض", v -> setControlsVisible(false)),
                button("قفل التطبيق", v -> enterLockTaskMode()),
                button("خروج من التطبيق", v -> safeExitApp())
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
        if (brightnessLabel != null) brightnessLabel.setText("السطوع: " + appSettings.brightnessPercent + "%");
    }

    private void saveSettingsFromFields() {
        appSettings.deviceId = value(deviceIdInput, appSettings.deviceId);
        appSettings.deviceName = value(deviceNameInput, appSettings.deviceName);
        appSettings.serverHost = value(serverHostInput, "");
        appSettings.serverPort = parsePort(value(serverPortInput, String.valueOf(appSettings.serverPort)), appSettings.serverPort);
        appSettings.autoDiscover = autoDiscoverCheck != null && autoDiscoverCheck.isChecked();
        appSettings.showModeOnLaunch = showModeCheck != null && showModeCheck.isChecked();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        applyScreenBrightness(appSettings.brightnessPercent);
        applyOrientation(appSettings.orientationMode);
        player.setVideoScaleMode(appSettings.videoScaleMode);
        refreshSettingsFields();
        renderInfo("تم حفظ الإعدادات.");
        hideSystemUi();
    }

    private void regenerateDeviceId() {
        appSettings.deviceId = "tablet-" + java.util.UUID.randomUUID();
        appSettings.save(this);
        stageCoreClient = new StageCoreClient(appSettings.deviceId, appSettings.deviceName);
        refreshSettingsFields();
        renderInfo("تم توليد ID جديد لهذا التابلت.");
    }

    private void setScale(String scale) {
        appSettings.videoScaleMode = scale;
        appSettings.save(this);
        player.setVideoScaleMode(scale);
        renderInfo("تم تغيير حجم الفيديو إلى: " + scale);
    }

    private void setOrientation(String orientation) {
        appSettings.orientationMode = orientation;
        appSettings.save(this);
        applyOrientation(orientation);
        renderInfo("تم تغيير اتجاه العرض إلى: " + orientation);
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
    }

    private void loadExternalOrSample() {
        manifestStore.tryLoadFromDiskOrSample(mediaResolver.manifestFile());
    }

    private void showResult(CommandResult result) {
        renderInfo(result.toString());
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
                + "\nمصدر المنفست: " + manifestStore.activeSource()
                + "\nالمشروع: " + manifest.showName
                + "\nالصورة: " + appSettings.videoScaleMode
                + " | الاتجاه: " + appSettings.orientationMode
                + " | السطوع: " + appSettings.brightnessPercent + "%"
                + cues);
    }

    private void setControlsVisible(boolean visible) {
        if (controlsPanel != null) controlsPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        player.setStatusVisible(false);
        if (visible) {
            refreshSettingsFields();
            renderInfo("لوحة الإعدادات مفتوحة.");
        }
        hideSystemUi();
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

    private String storagePermissionSummary() {
        boolean allFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        return "فحص الصلاحيات"
                + "\nAll files access: " + (allFiles ? "مفعّل" : "غير مفعّل")
                + "\nمجلد الفيديوات: " + (mediaResolver.baseDir().exists() ? "موجود" : "غير موجود")
                + "\nإذا الملفات ما تنقرأ، افتح صلاحيات التخزين وفعّل Allow management of all files.";
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

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void enterLockTaskMode() {
        try {
            startLockTask();
            renderInfo("تم طلب قفل التطبيق. إذا جهازك مفعل Screen Pinning أو Device Owner راح يمنع الخروج بالأزرار.");
        } catch (IllegalStateException error) {
            renderInfo("تعذر تفعيل قفل التطبيق من داخل التطبيق فقط. فعّل Screen Pinning من إعدادات أندرويد ثم جرب مرة ثانية.");
        }
        hideSystemUi();
    }

    private void safeExitApp() {
        if (discovery != null) discovery.stop();
        if (oscServer != null) oscServer.stop();
        finish();
    }

    private void saveSettingsFromFieldsWithoutRender() {
        appSettings.deviceId = value(deviceIdInput, appSettings.deviceId);
        appSettings.deviceName = value(deviceNameInput, appSettings.deviceName);
        appSettings.serverHost = value(serverHostInput, appSettings.serverHost);
        appSettings.serverPort = parsePort(value(serverPortInput, String.valueOf(appSettings.serverPort)), appSettings.serverPort);
        appSettings.autoDiscover = autoDiscoverCheck == null || autoDiscoverCheck.isChecked();
        appSettings.showModeOnLaunch = showModeCheck == null || showModeCheck.isChecked();
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
