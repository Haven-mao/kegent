package com.haven.kegent;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.haven.kegent.core.RunRoute;
import com.haven.kegent.route.RouteRepository;
import com.haven.kegent.ui.Ui;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

/** The route picker is a persistent library, not a destructive replacement of the last import. */
public final class RouteLibraryActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private RouteRepository repository;
    private LinearLayout entries;
    private TextView message;
    private boolean busy;
    private String currentName = "";
    private final ActivityResultLauncher<String[]> importPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importRoute);
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); repository = new RouteRepository(getFilesDir());
        LinearLayout root = Ui.column(this); root.setPadding(dp(16), dp(8), dp(16), dp(12));
        root.setBackgroundColor(Ui.color(this, R.color.ui_background)); setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        com.google.android.material.button.MaterialButton back = Ui.button(this, "返回", false); back.setOnClickListener(v -> finish()); header.addView(back);
        TextView title = Ui.text(this, "已有路线", 20, true); LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1); tp.setMarginStart(dp(12)); header.addView(title, tp); root.addView(header);
        com.google.android.material.button.MaterialButton defaultRoute = Ui.button(this, "使用默认路线", true);
        defaultRoute.setOnClickListener(v -> chooseDefault()); Ui.add(root, defaultRoute, 10);
        Ui.add(root, Ui.note(this, RouteRepository.DEFAULT_NAME), 4);
        com.google.android.material.button.MaterialButton importRoute = Ui.button(this, "导入 KML 并保存", false); importRoute.setId(R.id.route_library_import);
        importRoute.setOnClickListener(v -> { if (!busy) importPicker.launch(new String[] {"application/vnd.google-earth.kml+xml", "application/xml", "text/xml", "application/octet-stream"}); });
        Ui.add(root, importRoute, 10);
        message = Ui.note(this, "正在读取已保存路线…"); message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); Ui.add(root, message, 8);
        ScrollView scroll = new ScrollView(this); entries = Ui.column(this); scroll.addView(entries); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        reload();
    }
    private int dp(float x) { return Ui.dp(this, x); }
    private void ui(Runnable action) { runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) action.run(); }); }
    private void failure(Exception error) { ui(() -> { busy = false; message.setText(error.getMessage() == null ? "操作失败，请重试" : error.getMessage()); }); }
    private boolean available() {
        if (busy) return false;
        if (LocationMockService.latest.active()) { message.setText("请先结束正在运行的路线"); return false; }
        return true;
    }
    private void reload() {
        worker.execute(() -> {
            try {
                try (InputStream fallback = getResources().openRawResource(R.raw.dutpj_mutipoint)) { repository.preserveSelection(fallback, getSharedPreferences("workspace", MODE_PRIVATE).getString("route_name", "自定义路线")); }
                String selected;
                try (InputStream fallback = getResources().openRawResource(R.raw.dutpj_mutipoint)) { selected = repository.load(fallback, "自定义路线").name; }
                List<RouteRepository.SavedRoute> saved = repository.listSaved();
                ui(() -> {
                    currentName = selected; entries.removeAllViews(); message.setText(saved.isEmpty() ? "还没有保存的路线，可导入 KML 或在地图中绘制保存" : "点击使用路线；右侧可重命名或移除");
                    for (RouteRepository.SavedRoute route : saved) addRoute(route);
                });
            } catch (Exception e) { failure(e); }
        });
    }
    private void addRoute(RouteRepository.SavedRoute route) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(10), dp(8), dp(10));
        LinearLayout text = Ui.column(this); text.addView(Ui.text(this, route.name, 16, true));
        Ui.add(text, Ui.note(this, String.format(Locale.CHINA, "%.2f 米 · %d 点 · %s%s", route.meters, route.points, route.closed ? "闭合" : "往返", route.name.equals(currentName) ? " · 当前使用" : "")), 6);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        text.setOnClickListener(v -> choose(route.id)); text.setContentDescription("使用路线：" + route.name); text.setPadding(0, dp(6), dp(8), dp(6));
        com.google.android.material.button.MaterialButton actions = Ui.button(this, "管理", false); actions.setOnClickListener(v -> manage(route)); row.addView(actions);
        View card = Ui.card(this, row); card.setOnClickListener(v -> choose(route.id)); Ui.add(entries, card, 10);
    }
    private void choose(String id) {
        if (!available()) return; busy = true;
        worker.execute(() -> { try { repository.selectSaved(id); ui(() -> { setResult(RESULT_OK); finish(); }); } catch (Exception e) { failure(e); } });
    }
    private void chooseDefault() {
        if (!available()) return; busy = true;
        worker.execute(() -> {
            try (InputStream in = getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                repository.select(RunRoute.readKml(in, RouteRepository.DEFAULT_NAME), null);
                ui(() -> { setResult(RESULT_OK); finish(); });
            } catch (Exception e) { failure(e); }
        });
    }
    private void manage(RouteRepository.SavedRoute route) {
        if (!available()) return;
        new MaterialAlertDialogBuilder(this).setTitle(route.name).setItems(new String[] {"使用这条路线", "重命名", "从列表移除"}, (dialog, which) -> {
            if (which == 0) choose(route.id);
            else if (which == 1) nameDialog(route.name, name -> worker.execute(() -> { try { repository.renameSaved(route.id, name); ui(() -> { setResult(RESULT_OK); reload(); }); } catch (Exception e) { failure(e); } }));
            else new MaterialAlertDialogBuilder(this).setTitle("从路线列表移除？").setMessage("已应用的当前路线仍可继续使用。").setNegativeButton("取消", null)
                    .setPositiveButton("移除", (d, w) -> worker.execute(() -> { try { repository.deleteSaved(route.id); ui(this::reload); } catch (Exception e) { failure(e); } })).show();
        }).show();
    }
    private interface NameAction { void accept(String name); }
    private void nameDialog(String initial, NameAction action) {
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0);
        TextInputLayout name = Ui.field(this, "路线名称", initial, InputType.TYPE_CLASS_TEXT); Ui.add(form, name, 8);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("命名并保存路线").setView(form).setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            String value = Ui.value(name); if (value.isEmpty() || value.length() > 80) { name.setError("请填写 1–80 个字"); return; }
            if (!available()) return; action.accept(value); dialog.dismiss();
        })); dialog.show();
    }
    private void importRoute(Uri uri) {
        if (uri == null || !available()) return; busy = true; message.setText("正在读取 KML…");
        worker.execute(() -> {
            try {
                String name = "导入路线";
                try (android.database.Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0).replaceFirst("(?i)\\.kml$", "");
                }
                RunRoute imported;
                try (InputStream in = getContentResolver().openInputStream(uri)) { if (in == null) throw new java.io.IOException("无法读取文件"); imported = RunRoute.readKml(in, name); }
                String proposed = name.length() > 80 ? name.substring(0, 80) : name;
                ui(() -> { busy = false; nameDialog(proposed, value -> {
                    busy = true; message.setText("正在保存路线…");
                    worker.execute(() -> { try { repository.saveAndSelect(RouteRepository.rename(imported, value), null); ui(() -> { setResult(RESULT_OK); finish(); }); } catch (Exception e) { failure(e); } });
                }); });
            } catch (Exception e) { failure(e); }
        });
    }
    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
