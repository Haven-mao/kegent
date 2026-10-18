package com.haven.kgent;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.haven.kgent.core.HistoricalRun;
import com.haven.kgent.core.RunDateTime;
import com.haven.kgent.core.RunEngine;
import com.haven.kgent.core.RunRoute;
import com.haven.kgent.keep.KeepBridge;
import com.haven.kgent.ui.RoutePreviewView;
import com.haven.kgent.ui.Ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FakeGPSActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private LinearLayout root, liveForm, historyForm, review, compactStatus;
    private TextView routeName,
            routeDetails,
            status,
            metrics,
            paceHint,
            historyHint,
            connection,
            reviewText,
            error;
    private MaterialButton primary, toggle, stop, browse, reset, connect, export;
    private MaterialSwitch liveNatural, historyNatural;
    private RoutePreviewView routeView;
    private LinearProgressIndicator progress;
    private TextInputLayout speed,
            repeats,
            interval,
            altitude,
            accuracy,
            hStart,
            hEnd,
            hPace,
            hCadence,
            hWeight;
    private SharedPreferences prefs;
    private RunRoute route;
    private HistoricalRun prepared, exportRun;
    private KeepBridge.Session keepSession;
    private boolean history, busy, checking, binding;
    private RunEngine.State lastState;
    private String renderSignature;
    private final ActivityResultLauncher<String[]> routePicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocument(), this::importRoute);
    private final ActivityResultLauncher<String> permissions =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) startLive();
                        else showError("需要定位权限才能开始，请允许后重试");
                    });
    private final ActivityResultLauncher<String> exportPicker =
            registerForActivityResult(
                    new ActivityResultContracts.CreateDocument("application/gpx+xml"),
                    this::writeExport);
    private final Runnable update =
            new Runnable() {
                public void run() {
                    if (!isFinishing()) {
                        refreshLive();
                        handler.postDelayed(this, 500);
                    }
                }
            };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("workspace", MODE_PRIVATE);
        binding = true;
        buildScreen();
        binding = false;
        loadRoute();
    }

    private int dp(float x) {
        return Ui.dp(this, x);
    }

    private void buildScreen() {
        root = Ui.column(this);
        root.setBackgroundColor(Ui.color(this, R.color.ui_background));
        setContentView(root);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(8), dp(20), dp(4));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ka_squ);
        logo.setContentDescription(getString(R.string.app_name));
        top.addView(logo, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView title = Ui.text(this, getString(R.string.app_name), 23, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1);
        tp.setMarginStart(dp(12));
        top.addView(title, tp);
        MaterialButton help = Ui.button(this, "使用说明", false);
        help.setOnClickListener(v -> showHelp());
        top.addView(help, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(top);
        TabLayout tabs = new TabLayout(this);
        tabs.setBackgroundColor(Ui.color(this, R.color.ui_background));
        tabs.setTabTextColors(Ui.color(this, R.color.ui_muted), Ui.color(this, R.color.ui_accent));
        tabs.setSelectedTabIndicatorColor(Ui.color(this, R.color.ui_accent));
        tabs.addTab(tabs.newTab().setText("实时跑步"));
        tabs.addTab(tabs.newTab().setText("历史补跑"));
        root.addView(tabs, new LinearLayout.LayoutParams(-1, dp(48)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout shell = Ui.column(this);
        shell.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(shell);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 840;
        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        shell.addView(panes, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout left = Ui.column(this);
        left.setPadding(dp(20), dp(20), dp(20), dp(20));
        routeName = Ui.text(this, "正在读取路线", 19, true);
        routeDetails = Ui.note(this, "KML 路线将在这里显示");
        routeView = new RoutePreviewView(this);
        if (wide) {
            Ui.add(left, routeName, 0);
            Ui.add(left, routeDetails, 6);
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(240));
            previewParams.topMargin = dp(18);
            left.addView(routeView, previewParams);
        } else {
            LinearLayout summary = new LinearLayout(this);
            summary.setGravity(Gravity.CENTER_VERTICAL);
            summary.addView(routeView, new LinearLayout.LayoutParams(dp(108), dp(100)));
            LinearLayout routeInfo = Ui.column(this);
            routeName.setTextSize(17);
            Ui.add(routeInfo, routeName, 0);
            Ui.add(routeInfo, routeDetails, 8);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, -2, 1);
            infoParams.setMarginStart(dp(14));
            summary.addView(routeInfo, infoParams);
            left.addView(summary);
        }
        browse = Ui.button(this, "选择路线", false);
        browse.setOnClickListener(
                v ->
                        routePicker.launch(
                                new String[] {
                                    "application/vnd.google-earth.kml+xml",
                                    "application/xml",
                                    "text/xml",
                                    "application/octet-stream"
                                }));
        reset = Ui.button(this, "默认路线", false);
        reset.setOnClickListener(
                v -> {
                    if (!new File(getFilesDir(), "selected-route.kml").exists()) {
                        loadRoute();
                        return;
                    }
                    new File(getFilesDir(), "selected-route.kml").delete();
                    prefs.edit().remove("route_name").apply();
                    loadRoute();
                });
        Ui.pair(left, browse, reset, 12);
        status = Ui.text(this, "准备就绪", 16, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        metrics = Ui.note(this, "选择路线并设置速度后开始");
        progress = new LinearProgressIndicator(this);
        progress.setIndicatorColor(Ui.color(this, R.color.ui_accent));
        progress.setTrackColor(Ui.color(this, R.color.ui_tonal));
        progress.setTrackCornerRadius(dp(3));
        if (wide) {
            Ui.add(left, status, 20);
            Ui.add(left, metrics, 8);
            Ui.add(left, progress, 14);
        }
        View leftCard = Ui.card(this, left);
        LinearLayout right = Ui.column(this);
        right.setPadding(dp(20), dp(20), dp(20), dp(20));
        liveForm = liveForm();
        historyForm = historyForm();
        right.addView(liveForm);
        right.addView(historyForm);
        historyForm.setVisibility(View.GONE);
        View rightCard = Ui.card(this, right);
        if (wide) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, -2, 0.9f);
            l.setMarginEnd(dp(16));
            panes.addView(leftCard, l);
            panes.addView(rightCard, new LinearLayout.LayoutParams(0, -2, 1.1f));
        } else {
            panes.addView(leftCard, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(-1, -2);
            r.topMargin = dp(16);
            panes.addView(rightCard, r);
        }
        LinearLayout footer = Ui.column(this);
        footer.setPadding(dp(20), dp(8), dp(20), dp(12));
        footer.setBackgroundColor(Ui.color(this, R.color.ui_surface));
        if (!wide) {
            compactStatus = Ui.column(this);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            status.setTextSize(13);
            metrics.setTextSize(12);
            metrics.setGravity(Gravity.END);
            line.addView(status);
            LinearLayout.LayoutParams metricParams = new LinearLayout.LayoutParams(0, -2, 1);
            metricParams.setMarginStart(dp(10));
            line.addView(metrics, metricParams);
            Ui.add(compactStatus, line, 0);
            Ui.add(compactStatus, progress, 8);
            Ui.add(footer, compactStatus, 0);
        }
        error = Ui.note(this, "");
        error.setTextColor(Ui.color(this, R.color.ui_error));
        error.setVisibility(View.GONE);
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.add(footer, error, 0);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        primary = Ui.button(this, "开始运行", true);
        primary.setId(R.id.primary_action);
        primary.setOnClickListener(
                v -> {
                    if (history) onHistoryAction();
                    else if (LocationMockService.latest.active()) applySpeed();
                    else startLive();
                });
        toggle = Ui.button(this, "暂停", false);
        toggle.setOnClickListener(
                v ->
                        command(
                                LocationMockService.latest.state == RunEngine.State.PAUSED
                                        ? LocationMockService.RESUME
                                        : LocationMockService.PAUSE));
        stop = Ui.button(this, "结束", false);
        stop.setOnClickListener(v -> command(LocationMockService.STOP));
        buttons.addView(primary, new LinearLayout.LayoutParams(0, dp(56), 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, dp(56));
        sp.setMarginStart(dp(8));
        buttons.addView(toggle, sp);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-2, dp(56));
        ep.setMarginStart(dp(8));
        buttons.addView(stop, ep);
        Ui.add(footer, buttons, 4);
        root.addView(footer);
        tabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    public void onTabSelected(TabLayout.Tab tab) {
                        setMode(tab.getPosition() == 1);
                    }

                    public void onTabUnselected(TabLayout.Tab tab) {}

                    public void onTabReselected(TabLayout.Tab tab) {}
                });
        if (prefs.getBoolean("history", false)) tabs.getTabAt(1).select();
        else setMode(false);
    }

    private TextInputLayout field(String label, String key, String fallback, int type) {
        TextInputLayout f = Ui.field(this, label, prefs.getString(key, fallback), type);
        f.setTag(key);
        return f;
    }

    private LinearLayout liveForm() {
        LinearLayout form = Ui.column(this);
        Ui.add(form, Ui.text(this, "运行配置", 20, true), 0);
        Ui.add(form, Ui.note(this, "运行中可调整速度，切换页面后继续运行。"), 6);
        int decimal = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        speed = field("平均速度 · m/s", "speed", "3.00", decimal);
        speed.getEditText().setId(R.id.live_speed);
        Ui.add(form, speed, 20);
        MaterialButton slower = Ui.button(this, "− 0.1 m/s", false),
                faster = Ui.button(this, "+ 0.1 m/s", false);
        slower.setOnClickListener(v -> changeSpeed(-0.1));
        faster.setOnClickListener(v -> changeSpeed(0.1));
        Ui.pair(form, slower, faster, 4);
        paceHint = Ui.note(this, "");
        Ui.add(form, paceHint, 4);
        liveNatural = naturalPaceSwitch("live_natural");
        Ui.add(form, liveNatural, 12);
        Ui.add(form, Ui.note(this, "前 1 km 略快，之后平稳小幅变化；关闭后匀速运行。"), 2);
        repeats = field("路线次数", "repeats", "15", InputType.TYPE_CLASS_NUMBER);
        interval = field("更新间隔 · 秒", "interval", "0.3", decimal);
        Ui.pair(form, repeats, interval, 16);
        altitude = field("海拔 · 米", "altitude", "0", decimal | InputType.TYPE_NUMBER_FLAG_SIGNED);
        accuracy = field("定位精度 · 米", "accuracy", "5", decimal);
        Ui.pair(form, altitude, accuracy, 14);
        Ui.add(form, Ui.note(this, "开放路线到终点后折返，闭合路线循环运行。"), 16);
        watch(speed, this::updatePace);
        watch(repeats, this::updatePace);
        return form;
    }

    private LinearLayout historyForm() {
        LinearLayout form = Ui.column(this);
        Ui.add(form, Ui.text(this, "补一条历史记录", 20, true), 0);
        Ui.add(form, Ui.note(this, "设置过去的时间和配速，按所选路线生成轨迹。"), 6);
        LocalDateTime yesterday =
                LocalDateTime.now()
                        .minusDays(1)
                        .withHour(19)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0);
        hStart =
                field(
                        "开始时间",
                        "history_start",
                        RunDateTime.format(yesterday),
                        InputType.TYPE_CLASS_DATETIME);
        hStart.getEditText().setId(R.id.history_start);
        hEnd =
                field(
                        "结束时间",
                        "history_end",
                        RunDateTime.format(yesterday.plusMinutes(30)),
                        InputType.TYPE_CLASS_DATETIME);
        hEnd.getEditText().setId(R.id.history_end);
        datePicker(hStart);
        datePicker(hEnd);
        Ui.add(form, hStart, 20);
        Ui.add(form, hEnd, 14);
        Ui.add(form, Ui.note(this, "时区：" + ZoneId.systemDefault().getId()), 6);
        hPace =
                field(
                        "配速 · 分:秒/公里",
                        "history_pace",
                        "5:30",
                        InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        hPace.getEditText().setId(R.id.history_pace);
        Ui.add(form, hPace, 16);
        historyNatural = naturalPaceSwitch("history_natural");
        historyNatural.setOnCheckedChangeListener((button, checked) -> invalidateHistory());
        Ui.add(form, historyNatural, 12);
        Ui.add(form, Ui.note(this, "前 1 km 略快，之后平稳；总时长、距离和平均配速保持一致。"), 2);
        hCadence = field("步频 · 步/分钟", "history_cadence", "170", InputType.TYPE_CLASS_NUMBER);
        hWeight =
                field(
                        "体重 · kg",
                        "history_weight",
                        "65",
                        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Ui.pair(form, hCadence, hWeight, 14);
        historyHint = Ui.note(this, "距离由时间和配速计算");
        Ui.add(form, historyHint, 10);
        connection = Ui.note(this, "检查本机 Keep 和当前登录账号");
        connection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.add(form, connection, 20);
        Ui.add(form, Ui.note(this, "Keep 地图底图：记录详情 → 全屏 → 图层 → 点击 Street 上方缩略图 → 确定"), 8);
        connect = Ui.button(this, "检查 Keep 连接", false);
        connect.setOnClickListener(v -> inspectKeep());
        export = Ui.button(this, "导出 GPX", false);
        export.setOnClickListener(v -> exportGpx());
        Ui.pair(form, connect, export, 6);
        review = Ui.column(this);
        review.setPadding(dp(14), dp(14), dp(14), dp(14));
        review.setBackgroundColor(Ui.color(this, R.color.ui_tonal));
        reviewText = Ui.text(this, "", 14, false);
        review.addView(reviewText);
        Ui.add(form, review, 16);
        review.setVisibility(View.GONE);
        for (TextInputLayout f : new TextInputLayout[] {hStart, hEnd, hPace, hCadence, hWeight})
            watch(f, this::invalidateHistory);
        return form;
    }

    private MaterialSwitch naturalPaceSwitch(String key) {
        MaterialSwitch control = new MaterialSwitch(this);
        control.setText("自然配速");
        control.setTextSize(14);
        control.setTextColor(Ui.color(this, R.color.ui_text));
        control.setMinHeight(dp(48));
        control.setChecked(prefs.getBoolean(key, true));
        return control;
    }

    private void setMode(boolean history) {
        this.history = history;
        liveForm.setVisibility(history ? View.GONE : View.VISIBLE);
        historyForm.setVisibility(history ? View.VISIBLE : View.GONE);
        clearError();
        if (!binding) Ui.reveal(history ? historyForm : liveForm);
        updatePace();
        updateHistoryHint();
        refreshLive();
    }

    private void watch(TextInputLayout f, Runnable changed) {
        f.getEditText()
                .addTextChangedListener(
                        new TextWatcher() {
                            public void beforeTextChanged(
                                    CharSequence s, int st, int count, int after) {}

                            public void onTextChanged(
                                    CharSequence s, int st, int before, int count) {
                                if (!binding) {
                                    f.setError(null);
                                    changed.run();
                                }
                            }

                            public void afterTextChanged(Editable e) {}
                        });
    }

    private void datePicker(TextInputLayout f) {
        try {
            f.getEditText().setText(RunDateTime.format(RunDateTime.parse(Ui.value(f))));
        } catch (IllegalArgumentException ignored) {
            // Keep invalid user input visible so validation can explain the recovery.
        }
        f.getEditText().setFocusable(false);
        f.getEditText().setClickable(true);
        f.getEditText()
                .setOnClickListener(
                        v -> {
                            LocalDateTime current;
                            try {
                                current = RunDateTime.parse(Ui.value(f));
                            } catch (Exception e) {
                                current = LocalDateTime.now().minusDays(1);
                            }
                            LocalDateTime initial = current;
                            DatePickerDialog dialog =
                                    new DatePickerDialog(
                                            this,
                                            (view, y, m, d) ->
                                                    selectTime(
                                                            f,
                                                            initial.withYear(y)
                                                                    .withDayOfMonth(1)
                                                                    .withMonth(m + 1)
                                                                    .withDayOfMonth(d)),
                                            initial.getYear(),
                                            initial.getMonthValue() - 1,
                                            initial.getDayOfMonth());
                            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
                            dialog.show();
                        });
    }

    private void selectTime(TextInputLayout field, LocalDateTime initial) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        NumberPicker hours = timePart(row, "时", 23, initial.getHour());
        NumberPicker minutes = timePart(row, "分", 59, initial.getMinute());
        NumberPicker seconds = timePart(row, "秒", 59, initial.getSecond());
        new MaterialAlertDialogBuilder(this)
                .setTitle(field.getHint())
                .setView(row)
                .setNegativeButton("取消", null)
                .setPositiveButton(
                        "确定",
                        (dialog, which) -> {
                            hours.clearFocus();
                            minutes.clearFocus();
                            seconds.clearFocus();
                            field.getEditText()
                                    .setText(
                                            RunDateTime.format(
                                                    initial.withHour(hours.getValue())
                                                            .withMinute(minutes.getValue())
                                                            .withSecond(seconds.getValue())
                                                            .withNano(0)));
                        })
                .show();
    }

    private NumberPicker timePart(LinearLayout row, String label, int maximum, int value) {
        LinearLayout column = Ui.column(this);
        TextView title = Ui.text(this, label, 14, true);
        title.setGravity(Gravity.CENTER);
        column.addView(title, new LinearLayout.LayoutParams(-1, -2));
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(0);
        picker.setMaxValue(maximum);
        picker.setFormatter(number -> String.format(Locale.ROOT, "%02d", number));
        picker.setValue(value);
        picker.setContentDescription(label);
        picker.setWrapSelectorWheel(true);
        column.addView(picker, new LinearLayout.LayoutParams(-1, dp(148)));
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        return picker;
    }

    private void loadRoute() {
        primary.setEnabled(false);
        worker.execute(
                () -> {
                    try {
                        File selected = new File(getFilesDir(), "selected-route.kml");
                        RunRoute r;
                        try (InputStream in =
                                selected.exists()
                                        ? new FileInputStream(selected)
                                        : getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                            r =
                                    RunRoute.readKml(
                                            in,
                                            selected.exists()
                                                    ? prefs.getString("route_name", "自定义路线")
                                                    : "DUTPJ 操场路线");
                        }
                        RunRoute loaded = r;
                        onUi(() -> useRoute(loaded));
                    } catch (Exception e) {
                        onUi(() -> showError("路线无法读取，请重新选择 KML 文件"));
                    }
                });
    }

    private void useRoute(RunRoute selected) {
        route =
                LocationMockService.latest.active() && LocationMockService.activeRoute != null
                        ? LocationMockService.activeRoute
                        : selected;
        routeName.setText(route.name);
        routeDetails.setText(
                String.format(
                        Locale.CHINA,
                        "%.2f 米  /  %d 个点  /  %s",
                        route.lengthMeters,
                        route.size(),
                        route.closed ? "闭合路线" : "往返路线"));
        routeView.setRoute(route);
        invalidateHistory();
        updatePace();
        refreshLive();
    }

    private void importRoute(Uri uri) {
        if (uri == null) return;
        worker.execute(
                () -> {
                    File staged = new File(getFilesDir(), "route-import.tmp");
                    try {
                        String name = "自定义 KML 路线";
                        try (android.database.Cursor cursor =
                                getContentResolver()
                                        .query(
                                                uri,
                                                new String[] {OpenableColumns.DISPLAY_NAME},
                                                null,
                                                null,
                                                null)) {
                            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                        }
                        try (InputStream input = getContentResolver().openInputStream(uri);
                                FileOutputStream output = new FileOutputStream(staged)) {
                            if (input == null) throw new IllegalArgumentException("无法读取所选文件");
                            byte[] buf = new byte[8192];
                            int n, total = 0;
                            while ((n = input.read(buf)) > 0) {
                                total += n;
                                if (total > 4 * 1024 * 1024)
                                    throw new IllegalArgumentException("KML 文件不能超过 4 MB");
                                output.write(buf, 0, n);
                            }
                        }
                        RunRoute loaded;
                        try (InputStream input = new FileInputStream(staged)) {
                            loaded = RunRoute.readKml(input, name);
                        }
                        java.nio.file.Files.move(
                                staged.toPath(),
                                new File(getFilesDir(), "selected-route.kml").toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        prefs.edit().putString("route_name", name).apply();
                        onUi(() -> useRoute(loaded));
                    } catch (Exception e) {
                        staged.delete();
                        onUi(
                                () ->
                                        showError(
                                                e.getMessage() == null
                                                        ? "KML 路线读取失败"
                                                        : e.getMessage()));
                    }
                });
    }

    private double number(TextInputLayout f, double min, double max) {
        try {
            double v = Double.parseDouble(Ui.value(f));
            if (!Double.isFinite(v) || v < min || v > max) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            f.setError("请输入 " + min + " 至 " + max + " 之间的数值");
            throw new IllegalArgumentException("请检查标出的配置项");
        }
    }

    private void startLive() {
        clearError();
        if (route == null) {
            showError("请先选择有效路线");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        try {
            double velocity = number(speed, 0.1, 30);
            int count = (int) number(repeats, 1, 500);
            if (!Ui.value(repeats).matches("[0-9]+"))
                throw new IllegalArgumentException("路线次数必须为整数");
            double total = route.lengthMeters * count;
            if (total > 200000) throw new IllegalArgumentException("总距离不能超过 200 公里");
            Intent i =
                    new Intent(this, LocationMockService.class)
                            .setAction(LocationMockService.START)
                            .putExtra("speed", velocity)
                            .putExtra("natural_pace", liveNatural.isChecked())
                            .putExtra("repeats", count)
                            .putExtra("interval", (long) (number(interval, 0.1, 10) * 1000))
                            .putExtra("altitude", number(altitude, -500, 10000))
                            .putExtra("accuracy", number(accuracy, 0.1, 200))
                            .putExtra("route_name", route.name);
            ContextCompat.startForegroundService(this, i);
            primary.setEnabled(false);
            handler.postDelayed(this::refreshLive, 400);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void applySpeed() {
        try {
            double v = number(speed, 0.1, 30);
            startService(
                    new Intent(this, LocationMockService.class)
                            .setAction(LocationMockService.UPDATE)
                            .putExtra("speed", v));
            Snackbar.make(root, "速度已应用", Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void command(String action) {
        startService(new Intent(this, LocationMockService.class).setAction(action));
        handler.postDelayed(this::refreshLive, 80);
    }

    private void changeSpeed(double delta) {
        try {
            double v = Double.parseDouble(Ui.value(speed));
            speed.getEditText()
                    .setText(
                            String.format(
                                    Locale.ROOT, "%.2f", Math.max(0.1, Math.min(30, v + delta))));
            if (LocationMockService.latest.active()) applySpeed();
        } catch (Exception e) {
            speed.setError("请先输入有效速度");
        }
    }

    private void updatePace() {
        renderSignature = null;
        renderPace();
    }

    private void renderPace() {
        if (paceHint == null) return;
        try {
            double v = Double.parseDouble(Ui.value(speed));
            int count = Integer.parseInt(Ui.value(repeats));
            paceHint.setText(
                    String.format(
                            Locale.CHINA,
                            "配速 %s /km  ·  %.1f km/h%s",
                            HistoricalRun.formatPace(v),
                            v * 3.6,
                            route == null
                                    ? ""
                                    : String.format(
                                            Locale.CHINA,
                                            "  ·  目标 %.2f km",
                                            route.lengthMeters * count / 1000)));
        } catch (Exception e) {
            paceHint.setText("填写速度和次数后查看预计距离");
        }
    }

    private void refreshLive() {
        if (primary == null) return;
        RunEngine.Snapshot s = LocationMockService.latest;
        String signature =
                s.state
                        + "|"
                        + Math.round(s.distance * 100)
                        + "|"
                        + s.elapsedMillis / 1000
                        + "|"
                        + history
                        + "|"
                        + busy
                        + "|"
                        + (route != null)
                        + "|"
                        + (prepared != null);
        if (signature.equals(renderSignature)) return;
        renderSignature = signature;
        boolean active = s.active();
        liveNatural.setEnabled(!active && !busy);
        if (active) {
            paceHint.setText(
                    String.format(
                            Locale.CHINA,
                            "当前配速 %s /km  ·  %.2f m/s",
                            HistoricalRun.formatPace(s.speed),
                            s.speed));
        } else if (lastState != s.state) {
            renderPace();
        }
        if (compactStatus != null)
            compactStatus.setVisibility(!history || active ? View.VISIBLE : View.GONE);
        progress.setVisibility(!history || active ? View.VISIBLE : View.GONE);
        browse.setEnabled(!active && !busy);
        reset.setEnabled(!active && !busy);
        for (TextInputLayout f : new TextInputLayout[] {repeats, interval, altitude, accuracy})
            f.setEnabled(!active && !busy);
        toggle.setVisibility(active ? View.VISIBLE : View.GONE);
        stop.setVisibility(active ? View.VISIBLE : View.GONE);
        toggle.setText(s.state == RunEngine.State.PAUSED ? "继续" : "暂停");
        primary.setText(
                busy
                        ? "正在处理…"
                        : history
                                ? (active ? "结束后补跑" : prepared == null ? "预览历史记录" : "交给 Keep 同步")
                                : (active ? "应用速度" : "开始运行"));
        primary.setEnabled(!busy && route != null && (!history || !active));
        if (history && !active) {
            status.setText("历史记录预览");
            metrics.setText(
                    prepared == null
                            ? "先选时间和配速，再检查生成结果"
                            : String.format(
                                    Locale.CHINA,
                                    "%.2f km  /  %d 分 %02d 秒",
                                    prepared.distanceMeters / 1000,
                                    prepared.durationSeconds / 60,
                                    prepared.durationSeconds % 60));
            progress.setProgressCompat(0, false);
            routeView.setDistance(prepared == null ? 0 : prepared.distanceMeters, false);
            return;
        }
        String label =
                s.state == RunEngine.State.RUNNING
                        ? (history ? "实时运行中" : "正在运行")
                        : s.state == RunEngine.State.PAUSED
                                ? (history ? "实时已暂停" : "已暂停")
                                : s.state == RunEngine.State.FINISHED
                                        ? "本次运行已完成"
                                        : s.state == RunEngine.State.STOPPED
                                                ? "本次运行已结束"
                                                : s.state == RunEngine.State.ERROR
                                                        ? "运行未启动"
                                                        : "准备就绪";
        status.setText(label);
        if (lastState != s.state) {
            lastState = s.state;
            Ui.reveal(status);
            if (s.state == RunEngine.State.ERROR) showError(LocationMockService.lastError);
        }
        metrics.setText(
                s.targetDistance > 0
                        ? String.format(
                                Locale.CHINA,
                                "%.2f / %.2f km   ·   %02d:%02d",
                                s.distance / 1000,
                                s.targetDistance / 1000,
                                s.elapsedMillis / 60000,
                                (s.elapsedMillis / 1000) % 60)
                        : idleTarget());
        progress.setProgressCompat(
                s.targetDistance > 0 ? (int) (s.distance / s.targetDistance * 100) : 0,
                ValueAnimator.areAnimatorsEnabled());
        routeView.setDistance(s.distance, true);
    }

    private String idleTarget() {
        try {
            return route == null
                    ? "请选择路线"
                    : String.format(
                            Locale.CHINA,
                            "目标 %.2f km",
                            route.lengthMeters * Integer.parseInt(Ui.value(repeats)) / 1000);
        } catch (NumberFormatException e) {
            return "请完善运行配置";
        }
    }

    private HistoricalRun readHistory() {
        try {
            ZoneId zone = ZoneId.systemDefault();
            long
                    start =
                            RunDateTime.parse(Ui.value(hStart))
                                    .atZone(zone)
                                    .toInstant()
                                    .toEpochMilli(),
                    end = RunDateTime.parse(Ui.value(hEnd)).atZone(zone).toInstant().toEpochMilli();
            return new HistoricalRun(
                    route,
                    start,
                    end,
                    HistoricalRun.parsePace(Ui.value(hPace)),
                    Integer.parseInt(Ui.value(hCadence)),
                    Double.parseDouble(Ui.value(hWeight)),
                    zone.getId(),
                    System.currentTimeMillis(),
                    historyNatural.isChecked());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请填写有效的步频和体重");
        }
    }

    private void invalidateHistory() {
        prepared = null;
        if (review != null) review.setVisibility(View.GONE);
        updateHistoryHint();
        refreshLive();
    }

    private void updateHistoryHint() {
        if (historyHint == null || route == null) return;
        try {
            HistoricalRun run = readHistory();
            historyHint.setText(
                    String.format(
                            Locale.CHINA,
                            "预计 %.2f km，%d 分 %02d 秒，路线 %.1f 次",
                            run.distanceMeters / 1000,
                            run.durationSeconds / 60,
                            run.durationSeconds % 60,
                            run.distanceMeters / route.lengthMeters));
        } catch (Exception e) {
            historyHint.setText("填写完整时间与配速后自动计算距离");
        }
    }

    private void onHistoryAction() {
        clearError();
        if (prepared == null) {
            try {
                prepared = readHistory();
                reviewText.setText(
                        String.format(
                                Locale.CHINA,
                                "%s 至 %s\n"
                                        + "%.2f km · %d 分 %d 秒 · 配速 %s /km\n"
                                        + "%d 个轨迹点，约 %d 步。热量按体重估算。\n"
                                        + "确认后将记录交给本机 Keep，云端结果以 Keep 显示为准。",
                                Ui.value(hStart),
                                Ui.value(hEnd),
                                prepared.distanceMeters / 1000,
                                prepared.durationSeconds / 60,
                                prepared.durationSeconds % 60,
                                Ui.value(hPace),
                                prepared.sampleCount(),
                                prepared.totalSteps()));
                Ui.reveal(review);
                refreshLive();
            } catch (Exception e) {
                showError(e.getMessage());
            }
            return;
        }
        if (keepSession == null) {
            showError("请先检查 Keep 连接，再提交这条记录");
            inspectKeep();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("将这条记录交给 Keep？")
                .setMessage(
                        String.format(
                                Locale.CHINA,
                                "%s\n%s 至 %s\n距离 %.2f km\n\n会备份本地索引并重新打开 Keep。请在 Keep 中核对同步结果。",
                                keepSession.label(),
                                Ui.value(hStart),
                                Ui.value(hEnd),
                                prepared.distanceMeters / 1000))
                .setNegativeButton("继续编辑", null)
                .setPositiveButton("加入并打开 Keep", (d, w) -> queueHistory())
                .show();
    }

    private void inspectKeep() {
        if (checking) return;
        checking = true;
        connect.setEnabled(false);
        connection.setText("正在检查 Keep…");
        worker.execute(
                () -> {
                    try {
                        KeepBridge.Session session = new KeepBridge(this).inspect();
                        onUi(
                                () -> {
                                    keepSession = session;
                                    connection.setText(session.label());
                                });
                    } catch (Exception e) {
                        onUi(
                                () -> {
                                    keepSession = null;
                                    connection.setText(e.getMessage());
                                });
                    } finally {
                        onUi(
                                () -> {
                                    checking = false;
                                    connect.setEnabled(!busy);
                                });
                    }
                });
    }

    private void queueHistory() {
        HistoricalRun run = prepared;
        KeepBridge.Session session = keepSession;
        if (run == null || session == null) return;
        busy = true;
        setHistoryEnabled(false);
        refreshLive();
        worker.execute(
                () -> {
                    try {
                        KeepBridge bridge = new KeepBridge(this);
                        bridge.queue(run, session);
                        onUi(
                                () -> {
                                    reviewText.setText(
                                            "已加入 Keep 待上传列表。请在 Keep 中选择这条记录并上传，最终结果以 Keep 为准。");
                                    prepared = null;
                                });
                        try {
                            bridge.openSyncPage();
                        } catch (Exception openError) {
                            onUi(() -> showError("记录已加入，请手动打开 Keep 的运动数据同步页"));
                        }
                    } catch (Exception e) {
                        onUi(() -> showError(e.getMessage()));
                    } finally {
                        onUi(
                                () -> {
                                    busy = false;
                                    setHistoryEnabled(true);
                                    refreshLive();
                                });
                    }
                });
    }

    private void setHistoryEnabled(boolean enabled) {
        historyNatural.setEnabled(enabled);
        for (TextInputLayout f : new TextInputLayout[] {hStart, hEnd, hPace, hCadence, hWeight})
            f.setEnabled(enabled);
        connect.setEnabled(enabled && !checking);
        export.setEnabled(enabled);
    }

    private void exportGpx() {
        try {
            exportRun = readHistory();
            exportPicker.launch("kgent_" + exportRun.localDay() + ".gpx");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void writeExport(Uri uri) {
        if (uri == null || exportRun == null) return;
        HistoricalRun run = exportRun;
        worker.execute(
                () -> {
                    try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                        if (output == null) throw new IllegalStateException("无法写入所选文件");
                        StringBuilder xml =
                                new StringBuilder(
                                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx"
                                            + " version=\"1.1\" creator=\"kgent\""
                                            + " xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>kgent"
                                            + " historical run</name><trkseg>");
                        for (long t = 0;
                                ;
                                t =
                                        Math.min(
                                                run.durationSeconds,
                                                t + run.sampleIntervalSeconds())) {
                            RunRoute.Point p = run.route.at(run.distanceAtSecond(t));
                            xml.append("<trkpt lat=\"")
                                    .append(p.latitude)
                                    .append("\" lon=\"")
                                    .append(p.longitude)
                                    .append("\"><time>")
                                    .append(Instant.ofEpochMilli(run.startTime + t * 1000))
                                    .append("</time></trkpt>");
                            if (t == run.durationSeconds) break;
                        }
                        xml.append("</trkseg></trk></gpx>");
                        output.write(xml.toString().getBytes(StandardCharsets.UTF_8));
                        onUi(() -> Snackbar.make(root, "GPX 已导出", Snackbar.LENGTH_LONG).show());
                    } catch (Exception e) {
                        onUi(() -> showError("导出失败，请重新选择保存位置"));
                    }
                });
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("使用 kgent")
                .setMessage(
                        "实时跑步：选择 KML 路线并配置速度。真机需在开发者选项中将 kgent 设为模拟定位应用。运行中可暂停、继续和调速。\n\n"
                            + "历史补跑：单次选择过去的起止时间与配速，预览后交给 Keep。当前接入支持已提供 Root 的设备及 Keep"
                            + " 8.7.30。也可单独导出 GPX。\n\n"
                            + "起止时间可选到秒。自然配速在前 1 km 略快，后段仅有平滑小幅变化；关闭开关即可匀速。\n\n"
                            + "Keep 地图底图：记录详情 → 全屏 → 图层，然后点击 Street 上方的地图缩略图（不要只点文字）并确认。若选择“隐藏地图”或"
                            + " Gotham，只会显示纯色/深色背景和轨迹。\n\n"
                            + "路线请使用标准 GPS/WGS84 KML。交给 Keep 时会适配国内地图坐标，GPX 保留 GPS 坐标。\n\n"
                            + "定位精度是位置误差半径，不是加速度。")
                .setPositiveButton("知道了", null)
                .setNeutralButton(
                        "开发者选项",
                        (d, w) -> {
                            try {
                                startActivity(
                                        new Intent(
                                                android.provider.Settings
                                                        .ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
                            } catch (Exception e) {
                                showError("请手动打开系统开发者选项");
                            }
                        })
                .show();
    }

    private void clearError() {
        if (error != null) error.setVisibility(View.GONE);
    }

    private void showError(String message) {
        error.setText(message == null ? "操作未完成，请检查配置后重试" : message);
        error.setVisibility(View.VISIBLE);
    }

    private void onUi(Runnable action) {
        runOnUiThread(
                () -> {
                    if (!isFinishing() && !isDestroyed()) action.run();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(update);
        handler.post(update);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(update);
        SharedPreferences.Editor e = prefs.edit().putBoolean("history", history);
        e.putBoolean("live_natural", liveNatural.isChecked());
        e.putBoolean("history_natural", historyNatural.isChecked());
        for (TextInputLayout f :
                new TextInputLayout[] {
                    speed, repeats, interval, altitude, accuracy, hStart, hEnd, hPace, hCadence,
                    hWeight
                }) if (f != null) e.putString((String) f.getTag(), Ui.value(f));
        e.apply();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        worker.shutdown();
        super.onDestroy();
    }
}
