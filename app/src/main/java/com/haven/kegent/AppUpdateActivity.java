package com.haven.kegent;

import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;
import com.haven.kegent.ui.Ui;
import com.haven.kegent.update.*;
import java.io.File;
import java.util.Locale;

/** User-visible updating uses the system installer and never claims a download is an installation. */
public final class AppUpdateActivity extends AppCompatActivity {
    private UpdateViewModel model;
    private TextView status, offered, notes, downloadProgress;
    private MaterialButton check, download, install, cancel;
    private LinearProgressIndicator progress;
    private boolean awaitingPermission;
    private final ActivityResultLauncher<Intent> permission = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        boolean requested = awaitingPermission; awaitingPermission = false;
        if (requested && getPackageManager().canRequestPackageInstalls()) install();
        else if (requested) toast("尚未允许安装更新，已下载的安装包会保留");
    });
    private final ActivityResultLauncher<Intent> installer = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (model != null) {
            try {
                UpdateViewModel.State state = model.current();
                if (state != null && state.offer != null && ApkVerifier.version(ApkVerifier.installed(this)) >= state.offer.versionCode)
                    toast("已安装更新版本");
                else toast("当前应用尚未更新，可再次点击安装");
            } catch (Exception e) { toast("请重新打开应用确认版本"); }
        }
    });
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); awaitingPermission = state != null && state.getBoolean("update_permission");
        model = new ViewModelProvider(this).get(UpdateViewModel.class); buildScreen();
        model.state().observe(this, this::render); model.start();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { leave(); } });
    }
    private int dp(float n) { return Ui.dp(this, n); }
    private void buildScreen() {
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.color(this, R.color.ui_background)); setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(16), dp(6), dp(16), dp(4));
        MaterialButton back = Ui.button(this, "返回", false); back.setOnClickListener(v -> leave()); header.addView(back);
        TextView title = Ui.text(this, "应用更新", 20, true); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.setMarginStart(dp(12)); header.addView(title, tp);
        MaterialButton options = Ui.button(this, "更新来源", false); options.setOnClickListener(v -> source()); header.addView(options); root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = Ui.column(this); body.setPadding(dp(20), dp(18), dp(20), dp(20)); scroll.addView(body);
        String currentVersion;
        try { currentVersion = ApkVerifier.installed(this).versionName; } catch (Exception e) { currentVersion = "未知"; }
        Ui.add(body, Ui.text(this, "kegent · 当前版本 " + currentVersion, 18, true), 0);
        status = Ui.text(this, "正在检查新版本…", 16, true); status.setId(R.id.update_status); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); Ui.add(body, status, 20);
        offered = Ui.note(this, ""); Ui.add(body, offered, 8);
        progress = new LinearProgressIndicator(this); progress.setIndicatorColor(Ui.color(this, R.color.ui_accent)); progress.setMax(1000); Ui.add(body, progress, 18);
        downloadProgress = Ui.note(this, ""); Ui.add(body, downloadProgress, 8);
        notes = Ui.text(this, "", 14, false); notes.setTextIsSelectable(true); Ui.add(body, notes, 20);
        check = Ui.button(this, "检查更新", false); check.setId(R.id.update_check); check.setOnClickListener(v -> model.check()); Ui.add(body, check, 24);
        download = Ui.button(this, "下载更新", true); download.setId(R.id.update_download); download.setOnClickListener(v -> model.download()); Ui.add(body, download, 8);
        install = Ui.button(this, "安装更新", true); install.setId(R.id.update_install); install.setOnClickListener(v -> install()); Ui.add(body, install, 8);
        cancel = Ui.button(this, "暂停", false); cancel.setOnClickListener(v -> model.cancel()); Ui.add(body, cancel, 8);
        MaterialButton releases = Ui.button(this, "查看发布页", false); releases.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateTransfer.RELEASE_PAGE))); } catch (ActivityNotFoundException e) { toast("未找到可打开发布页的浏览器"); }
        }); Ui.add(body, releases, 8);
        Ui.add(body, Ui.note(this, "更新会保留本机路线和设置。安装时按系统提示确认，路线运行期间请先结束运行。"), 18);
    }
    private static String size(long bytes) { return String.format(Locale.CHINA, "%.1f MB", bytes / 1048576.0); }
    private void render(UpdateViewModel.State state) {
        if (state == null) return;
        status.setText(state.message);
        UpdateViewModel.Phase phase = state.phase; boolean checking = phase == UpdateViewModel.Phase.CHECKING;
        boolean downloading = phase == UpdateViewModel.Phase.DOWNLOADING, verifying = phase == UpdateViewModel.Phase.VERIFYING;
        boolean busy = checking || downloading || verifying;
        offered.setText(state.offer == null ? "" : "发布版本 " + state.offer.versionName + " · " + size(state.offer.sizeBytes));
        offered.setVisibility(state.offer == null ? View.GONE : View.VISIBLE);
        notes.setText(state.offer == null ? "" : state.offer.releaseNotes); notes.setVisibility(state.offer == null || state.offer.releaseNotes.isEmpty() ? View.GONE : View.VISIBLE);
        if (progress.isIndeterminate() != (checking || verifying)) { progress.setVisibility(View.INVISIBLE); progress.setIndeterminate(checking || verifying); }
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (downloading && state.offer != null) progress.setProgressCompat((int) (state.done * 1000 / state.offer.sizeBytes), false);
        downloadProgress.setText(downloading && state.offer != null ? size(state.done) + " / " + size(state.offer.sizeBytes) : "");
        downloadProgress.setVisibility(downloading ? View.VISIBLE : View.GONE);
        check.setEnabled(!busy); check.setText(phase == UpdateViewModel.Phase.ERROR || phase == UpdateViewModel.Phase.UNPUBLISHED ? "重新检查" : "检查更新");
        boolean canDownload = state.offer != null && (phase == UpdateViewModel.Phase.AVAILABLE || phase == UpdateViewModel.Phase.ERROR);
        download.setVisibility(canDownload ? View.VISIBLE : View.GONE); download.setEnabled(!busy);
        File partial = state.offer == null ? null : new File(model.directory(), UpdateTransfer.apk(model.directory(), state.offer).getName() + ".part");
        download.setText(partial != null && partial.isFile() ? "继续下载" : "下载更新");
        install.setVisibility(phase == UpdateViewModel.Phase.READY ? View.VISIBLE : View.GONE);
        cancel.setVisibility(busy ? View.VISIBLE : View.GONE); cancel.setText(downloading ? "暂停下载" : "取消");
    }
    private void install() {
        if (LocationMockService.latest.active()) { toast("请先结束正在运行的路线，再安装更新"); return; }
        UpdateViewModel.State state = model.current(); if (state == null || state.offer == null) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            new MaterialAlertDialogBuilder(this).setTitle("允许安装更新")
                    .setMessage("请在系统设置中允许 kegent 安装应用，返回后继续确认本次更新。")
                    .setNegativeButton("暂不安装", null).setPositiveButton("打开设置", (dialog, which) -> {
                        try { awaitingPermission = true; permission.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); }
                        catch (ActivityNotFoundException e) { awaitingPermission = false; toast("请在系统设置中允许 kegent 安装应用后重试"); }
                    }).show(); return;
        }
        model.prepareInstall(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (LocationMockService.latest.active()) { toast("请先结束正在运行的路线"); return; }
            UpdateViewModel.State ready = model.current(); if (ready == null || ready.offer == null) return;
            File apk = UpdateTransfer.apk(model.directory(), ready.offer);
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", apk);
                Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT, true);
                intent.setClipData(ClipData.newRawUri("kegent update", uri)); installer.launch(intent);
            } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) { toast("无法打开系统安装器，请稍后重试"); }
        });
    }
    private void source() {
        UpdateViewModel.State state = model.current();
        if (state != null && (state.phase == UpdateViewModel.Phase.DOWNLOADING || state.phase == UpdateViewModel.Phase.VERIFYING)) { toast("请先暂停下载后再更换更新来源"); return; }
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0);
        TextInputLayout url = Ui.field(this, "更新清单或 GitHub Release API（HTTPS）", model.source(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); Ui.add(form, url, 8);
        Ui.add(form, Ui.note(this, "默认使用 kegent 官方仓库。更换镜像后仍会核对安装包签名。"), 10);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("更新来源").setView(form).setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", (d, w) -> { try { model.setSource(UpdateTransfer.DEFAULT_SOURCE); model.check(); } catch (Exception e) { toast("无法恢复更新来源"); } }).setPositiveButton("保存并检查", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try { model.setSource(Ui.value(url)); dialog.dismiss(); model.check(); } catch (Exception e) { url.setError("请填写有效 HTTPS 地址"); }
        })); dialog.show();
    }
    private void leave() {
        UpdateViewModel.State state = model.current();
        if (state != null && state.phase == UpdateViewModel.Phase.DOWNLOADING) { model.cancel(); toast("下载已暂停，下次打开可继续"); }
        finish();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("update_permission", awaitingPermission); }
}
