package com.haven.kegent;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.haven.kegent.core.RunRoute;
import com.haven.kegent.route.*;
import com.haven.kegent.ui.RouteHandleOverlay;
import com.haven.kegent.ui.RouteDrawOverlay;
import com.haven.kegent.ui.Ui;
import com.haven.kegent.ui.RealMapStyle;
import org.json.*;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.*;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.*;

/** Native, WGS84 route editor. Drafts never replace the selected route until Apply succeeds. */
public final class RouteEditorActivity extends AppCompatActivity implements RouteHandleOverlay.Listener {
    private enum InputMode { POINT, DRAW, MOVE }
    private static final String DEFAULT_STYLE = RealMapStyle.DEFAULT_STYLE;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor(), storage = Executors.newSingleThreadExecutor();
    private final ExecutorService searchWorker = Executors.newSingleThreadExecutor();
    private PlaceSearch placeSearch;
    private Future<?> searchTask;
    private int searchRevision;
    private final WalkingRoutePlanner planner = new WalkingRoutePlanner();
    private final LinkedHashMap<String, List<RunRoute.Point>> cache = new LinkedHashMap<String, List<RunRoute.Point>>(32, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<RunRoute.Point>> entry) { return size() > 60; }
    };
    private final ArrayDeque<String> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private RouteDraft draft = new RouteDraft();
    private RouteAssembly assembly = new RouteAssembly();
    private RouteDraft currentTrack;
    private JSONObject recoverableDraft;
    private final EnumMap<RouteDraft.Mode, RouteDraft> modeDrafts = new EnumMap<>(RouteDraft.Mode.class);
    private RouteRepository repository;
    private SharedPreferences prefs;
    private MapView mapView;
    private MapLibreMap map;
    private RealMapStyle realMapStyle;
    private RouteHandleOverlay overlay;
    private RouteDrawOverlay drawing;
    private TextView status, hint, mapNotice;
    private MaterialButton save, back, undoButton, redoButton, delete, segment, track, start, retry;
    private MaterialButtonToggleGroup inputModes;
    private InputMode inputMode = InputMode.POINT;
    private boolean wideTools;
    private boolean geometryDirty = true, dragRenderQueued, draftTouched;
    private final Runnable dragRender = () -> { dragRenderQueued = false; render(); };
    private MaterialSwitch closed, smooth;
    private TabLayout modes;
    private Future<?> planningTask;
    private int revision, selected = -1;
    private boolean destroyed, loading = true, binding, planning, saving, pickingStart;
    private String requestError = "";
    private double cameraLat = Double.NaN, cameraLon, cameraZoom = 17;
    private final Runnable autoSave = this::persist;
    private final ActivityResultLauncher<String> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), allowed -> { if (allowed) locate(); else message("未开启定位，仍可移动地图选点"); });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("route_editor", MODE_PRIVATE);
        try { inputMode = InputMode.valueOf(prefs.getString("input_mode", "POINT")); } catch (IllegalArgumentException ignored) { }
        repository = new RouteRepository(getFilesDir());
        MapLibre.getInstance(this);
        buildScreen(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { leave(); }
        });
        storage.execute(() -> {
            String warning = ""; JSONObject saved = null;
            try { saved = repository.readDraft(); } catch (Exception e) { warning = "上次草稿无法恢复，已保留原文件"; }
            RouteDraft restored = null;
            try {
                if (state != null && state.getString("editor_draft") != null)
                    restored = RouteDraft.fromJson(new JSONObject(state.getString("editor_draft")));
            } catch (Exception e) { warning = "草稿格式无效，已保留原文件"; }
            RouteDraft trackFit = null;
            try (InputStream in = getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                RunRoute current = repository.load(in, getSharedPreferences("workspace", MODE_PRIVATE).getString("route_name", "自定义路线"));
                trackFit = RouteGeometry.fitTrack(current);
                if (restored == null) { restored = new RouteDraft(); restored.center = RouteGeometry.center(current); }
            } catch (Exception ignored) { }
            RouteDraft result = restored == null ? new RouteDraft() : restored;
            JSONObject savedState = state != null ? saved : null; String note = warning; RouteDraft initialTrack = trackFit; JSONObject previousDraft = saved;
            main.post(() -> {
                if (destroyed) return;
                draft = result; currentTrack = initialTrack; recoverableDraft = previousDraft; geometryDirty = true;
                draftTouched = state != null;
                modeDrafts.put(draft.mode, draft);
                if (savedState != null) restoreCacheAndCamera(savedState);
                if (state != null && state.containsKey("editor_lat")) {
                    cameraLat = state.getDouble("editor_lat"); cameraLon = state.getDouble("editor_lon"); cameraZoom = state.getDouble("editor_zoom", 17);
                }
                loading = false; syncControls(); render();
                if (map != null) { restoreCamera(); plan(); }
                if (!note.isEmpty()) message(note);
            });
        });
        placeSearch = new PlaceSearch(getCacheDir());
    }

    private void buildScreen(Bundle state) {
        wideTools = getResources().getConfiguration().screenWidthDp >= 800;
        LinearLayout root = Ui.column(this); root.setBackgroundColor(Ui.color(this, R.color.ui_background)); setContentView(root);
        LinearLayout header = row(); header.setPadding(dp(10), dp(2), dp(10), dp(2));
        back = button("返回", v -> leave()); header.addView(back);
        TextView title = Ui.text(this, "地图绘制", 18, true);
        LinearLayout.LayoutParams titleParams = wideTools ? new LinearLayout.LayoutParams(-2, -2) : new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMarginStart(dp(12)); header.addView(title, titleParams);
        header.addView(button("搜索", v -> searchPlaces()));
        header.addView(button("更多", v -> more())); root.addView(header);
        modes = new TabLayout(this); modes.setTabTextColors(Ui.color(this, R.color.ui_muted), Ui.color(this, R.color.ui_accent));
        modes.setSelectedTabIndicatorColor(Ui.color(this, R.color.ui_accent));
        for (String name : new String[] {"沿道路", "操场", "手动"}) modes.addTab(modes.newTab().setText(name));
        modes.setTabMode(TabLayout.MODE_FIXED);
        if (wideTools) {
            LinearLayout.LayoutParams navigation = new LinearLayout.LayoutParams(dp(252), dp(48)); navigation.setMarginStart(dp(24));
            header.addView(modes, 2, navigation); header.addView(new View(this), 3, new LinearLayout.LayoutParams(0, 1, 1));
        } else root.addView(modes, new LinearLayout.LayoutParams(-1, dp(44)));
        modes.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                if (binding || loading) return;
                if (saving) { syncControls(); return; }
                RouteDraft.Mode next = RouteDraft.Mode.values()[tab.getPosition()];
                remember(); modeDrafts.put(draft.mode, draft);
                RouteDraft nextDraft = modeDrafts.get(next);
                if (nextDraft == null) {
                    nextDraft = new RouteDraft(); nextDraft.mode = next;
                    nextDraft.center = map == null ? draft.center : point(map.getCameraPosition().target);
                    nextDraft.name = next == RouteDraft.Mode.TRACK ? "操场路线" : "地图路线";
                }
                draft = nextDraft; pickingStart = false; selected = -1;
                if (next == RouteDraft.Mode.TRACK && !draft.trackInitialized) initializeTrack(draft);
                edited(); fit();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        hint = Ui.note(this, "正在恢复草稿…"); hint.setTextSize(12);
        LinearLayout workspace = new LinearLayout(this); workspace.setOrientation(wideTools ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout frame = new FrameLayout(this);
        workspace.addView(frame, wideTools ? new LinearLayout.LayoutParams(0, -1, 1) : new LinearLayout.LayoutParams(-1, 0, 1));
        mapView = new MapView(this, MapLibreMapOptions.createFromAttributes(this).textureMode(true));
        mapView.setId(R.id.editor_map);
        mapView.onCreate(state); frame.addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        GestureDetector taps = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                if (map != null && !loading && !saving && (draft.mode == RouteDraft.Mode.TRACK || inputMode == InputMode.POINT)) {
                    mapClick(point(map.getProjection().fromScreenLocation(new PointF(event.getX(), event.getY()))), false);
                    mapView.performClick();
                }
                return true;
            }
            @Override public void onLongPress(MotionEvent event) {
                if (map != null && !loading && !saving && (draft.mode == RouteDraft.Mode.TRACK || inputMode == InputMode.POINT))
                    mapClick(point(map.getProjection().fromScreenLocation(new PointF(event.getX(), event.getY()))), true);
            }
        });
        taps.setOnDoubleTapListener(null);
        mapView.setOnTouchListener((view, event) -> {
            if (draft.mode == RouteDraft.Mode.TRACK || inputMode == InputMode.POINT) taps.onTouchEvent(event);
            return false;
        });
        overlay = new RouteHandleOverlay(this); overlay.listener = this; frame.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        drawing = new RouteDrawOverlay(this, mapView); drawing.setId(R.id.editor_draw_surface);
        drawing.listener = new RouteDrawOverlay.Listener() {
            @Override public void strokeStarted() { cancelPlanning(); status.setText("正在绘制，松手完成这一笔…"); }
            @Override public void strokeFinished(List<RunRoute.Point> points, double tolerance) { finishStroke(points, tolerance); }
            @Override public void strokeCancelled() { if (!destroyed && !saving) { render(); plan(); } }
        };
        frame.addView(drawing, new FrameLayout.LayoutParams(-1, -1));
        mapNotice = Ui.note(this, "正在加载地图…"); mapNotice.setPadding(dp(10), dp(6), dp(10), dp(6));
        mapNotice.setBackgroundColor(Ui.color(this, R.color.ui_surface));
        FrameLayout.LayoutParams noticeParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); frame.addView(mapNotice, noticeParams);
        LinearLayout mapTools = Ui.column(this); mapTools.addView(button("＋", v -> zoom(1))); mapTools.addView(button("－", v -> zoom(-1)));
        mapTools.addView(button("全览", v -> fit())); mapTools.addView(button("定位", v -> locate()));
        FrameLayout.LayoutParams toolsParams = new FrameLayout.LayoutParams(dp(64), -2, Gravity.END | Gravity.CENTER_VERTICAL);
        toolsParams.setMarginEnd(dp(6)); frame.addView(mapTools, toolsParams);
        mapView.getMapAsync(ready -> {
            if (destroyed) return;
            map = ready; overlay.map = map; drawing.map = map;
            map.getUiSettings().setRotateGesturesEnabled(false); map.getUiSettings().setTiltGesturesEnabled(false);
            map.getUiSettings().setDoubleTapGesturesEnabled(false); map.getUiSettings().setQuickZoomGesturesEnabled(false);
            map.getUiSettings().setLogoEnabled(false); map.getUiSettings().setAttributionEnabled(true);
            map.addOnCameraMoveListener(overlay::reproject);
            map.addOnCameraIdleListener(() -> { overlay.reproject(); schedulePersist(); });
            realMapStyle = new RealMapStyle(this, mapView, map, mapNotice, overlay::reproject);
            loadStyle(); if (!loading) { restoreCamera(); plan(); }
        });
        status = Ui.text(this, "正在读取路线…", 14, true); status.setId(R.id.editor_status);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout controls = Ui.column(this); controls.setPadding(dp(10), dp(8), dp(10), dp(6));
        LinearLayout toolHost = Ui.column(this); toolHost.setBackgroundColor(Ui.color(this, R.color.ui_surface));
        ScrollView toolScroll = new ScrollView(this); toolScroll.setFillViewport(false); toolScroll.addView(controls);
        if (wideTools) {
            workspace.addView(toolHost, new LinearLayout.LayoutParams(dp(224), -1));
            toolHost.addView(toolScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            Ui.add(controls, status, 0); Ui.add(controls, hint, 8);
        } else {
            toolScroll.setVisibility(View.GONE); workspace.addView(toolScroll, new LinearLayout.LayoutParams(-1, dp(180)));
            LinearLayout information = Ui.column(this); information.setPadding(dp(10), dp(6), dp(10), dp(6));
            information.setBackgroundColor(Ui.color(this, R.color.ui_surface));
            status.setMaxLines(2); hint.setMaxLines(2); Ui.add(information, status, 0); Ui.add(information, hint, 2);
            frame.addView(information, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        }
        inputModes = new MaterialButtonToggleGroup(this); inputModes.setSingleSelection(true); inputModes.setSelectionRequired(true);
        int[] inputIds = {R.id.editor_input_point, R.id.editor_input_draw, R.id.editor_input_move};
        String[] inputLabels = {"点选", "画线", "移动"};
        for (int i = 0; i < inputIds.length; i++) {
            MaterialButton b = button(inputLabels[i], v -> { }); b.setId(inputIds[i]); b.setCheckable(true); b.setTextSize(13);
            b.setPadding(dp(6), b.getPaddingTop(), dp(6), b.getPaddingBottom());
            inputModes.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        inputModes.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || binding || saving || loading) return;
            drawing.cancelStroke();
            inputMode = id == R.id.editor_input_draw ? InputMode.DRAW : id == R.id.editor_input_move ? InputMode.MOVE : InputMode.POINT;
            prefs.edit().putString("input_mode", inputMode.name()).apply(); render();
        });
        if (wideTools) Ui.add(controls, inputModes, 12);
        LinearLayout actionRow = wideTools ? Ui.column(this) : row();
        undoButton = button("撤销", v -> history(false)); redoButton = button("重做", v -> history(true));
        delete = button("删除选点", v -> deletePoint());
        segment = button("本段手绘", v -> toggleSegment()); track = button("跑道参数", v -> trackParameters());
        start = button("设置起跑点", v -> { if (!saving) { pickingStart = !pickingStart; render(); } });
        if (wideTools) {
            Ui.pair(actionRow, undoButton, redoButton, 0);
            for (MaterialButton b : new MaterialButton[] {delete, segment, track, start}) Ui.add(actionRow, b, 4);
            Ui.add(controls, actionRow, 12);
        } else {
            HorizontalScrollView actions = new HorizontalScrollView(this); actions.setHorizontalScrollBarEnabled(false); actions.addView(actionRow);
            for (MaterialButton b : new MaterialButton[] {undoButton, redoButton, delete, segment, track, start}) actionRow.addView(b);
            Ui.add(controls, actions, 0);
        }
        LinearLayout switches = wideTools ? Ui.column(this) : row();
        closed = new MaterialSwitch(this); closed.setText("闭合路线"); smooth = new MaterialSwitch(this); smooth.setText("平滑曲线");
        switches.addView(closed, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams smoothParams = new LinearLayout.LayoutParams(-2, -2); if (!wideTools) smoothParams.setMarginStart(dp(20));
        switches.addView(smooth, smoothParams); Ui.add(controls, switches, 8);
        closed.setOnCheckedChangeListener((v, value) -> { if (!binding && !loading && !saving) { remember(); draft.closed = value; edited(); } });
        smooth.setOnCheckedChangeListener((v, value) -> { if (!binding && !loading && !saving) { remember(); draft.smooth = value; edited(); } });
        LinearLayout footer = wideTools ? Ui.column(this) : row(); footer.setPadding(dp(8), dp(4), dp(8), dp(6));
        footer.setBackgroundColor(Ui.color(this, R.color.ui_surface));
        retry = button("重新规划", v -> { requestError = ""; plan(); });
        save = Ui.button(this, "保存并应用", true); save.setOnClickListener(v -> nameAndSave());
        if (wideTools) {
            Ui.add(footer, retry, 0); Ui.add(footer, save, 4); toolHost.addView(footer);
        } else {
            Ui.add(controls, retry, 4);
            footer.addView(inputModes, new LinearLayout.LayoutParams(0, dp(48), 1));
            MaterialButton tools = button("工具", v -> { toolScroll.setVisibility(toolScroll.getVisibility() == View.GONE ? View.VISIBLE : View.GONE); });
            footer.addView(tools, new LinearLayout.LayoutParams(dp(48), dp(48)));
            save.setTextSize(13); save.setPadding(dp(6), save.getPaddingTop(), dp(6), save.getPaddingBottom());
            footer.addView(save, new LinearLayout.LayoutParams(dp(108), dp(48))); workspace.addView(footer);
        }
    }

    private int dp(float value) { return Ui.dp(this, value); }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private MaterialButton button(String text, View.OnClickListener click) {
        MaterialButton b = Ui.button(this, text, false); b.setPadding(dp(10), b.getPaddingTop(), dp(10), b.getPaddingBottom());
        b.setOnClickListener(click); return b;
    }
    private static RunRoute.Point point(LatLng p) { return new RunRoute.Point(p.getLatitude(), p.getLongitude()); }
    private static LatLng latLng(RunRoute.Point p) { return new LatLng(p.latitude, p.longitude); }
    private String endpoint() { return prefs.getString("endpoint", WalkingRoutePlanner.DEFAULT_ENDPOINT); }

    private void syncControls() {
        binding = true; modes.selectTab(modes.getTabAt(draft.mode.ordinal()));
        closed.setChecked(draft.mode == RouteDraft.Mode.TRACK || draft.closed); smooth.setChecked(draft.smooth);
        closed.setEnabled(draft.mode != RouteDraft.Mode.TRACK && !saving);
        smooth.setEnabled(draft.mode == RouteDraft.Mode.MANUAL && !saving); binding = false;
    }
    private void remember() {
        try { undo.push(draft.json().toString()); while (undo.size() > 40) undo.removeLast(); redo.clear(); }
        catch (Exception e) { message("无法记录撤销状态"); }
    }
    private void history(boolean forward) {
        if (saving || loading) return;
        ArrayDeque<String> source = forward ? redo : undo, target = forward ? undo : redo;
        if (source.isEmpty()) return;
        try { target.push(draft.json().toString()); draft = RouteDraft.fromJson(new JSONObject(source.pop())); selected = -1; pickingStart = false; edited(); }
        catch (Exception e) { message("无法恢复这一步编辑"); }
    }
    private void edited() {
        revision++; geometryDirty = true; draftTouched = true; modeDrafts.put(draft.mode, draft);
        requestError = ""; syncControls(); schedulePersist(); plan();
    }
    private void finishStroke(List<RunRoute.Point> raw, double tolerance) {
        if (loading || saving || destroyed || draft.mode == RouteDraft.Mode.TRACK) return;
        try {
            int count = draft.anchors.size(), limit = draft.mode == RouteDraft.Mode.ROAD ? 20 : 500;
            List<RunRoute.Point> points = FreehandStroke.prepare(raw, tolerance,
                    Math.max(0, limit - count + (count > 0 ? 1 : 0)), draft.mode == RouteDraft.Mode.ROAD);
            if (count > 0) {
                double gap = RunRoute.distance(draft.anchors.get(count - 1).point, points.get(0));
                if (gap > Math.max(15, tolerance * 8))
                    throw new IllegalArgumentException("请从上一段终点附近继续绘制，或先新建路线");
                if (gap < Math.max(.5, tolerance / 2)) points.remove(0);
            }
            if (count + points.size() > limit)
                throw new IllegalArgumentException("控制点数量已达上限，请缩短这一笔或新建路线");
            remember();
            for (RunRoute.Point point : points) draft.anchors.add(new RouteDraft.Anchor(point));
            selected = draft.anchors.size() - 1; edited();
        } catch (Exception e) { message(e.getMessage()); render(); plan(); }
    }
    private void mapClick(RunRoute.Point p, boolean insert) {
        if (draft.mode == RouteDraft.Mode.TRACK) {
            remember();
            if (pickingStart) {
                updateStart(p);
                pickingStart = false;
            } else draft.center = p;
            edited(); return;
        }
        int limit = draft.mode == RouteDraft.Mode.ROAD ? 20 : 500;
        if (draft.anchors.size() >= limit) { message("当前模式最多 " + limit + " 个选点"); return; }
        remember(); int index = draft.anchors.size();
        if (insert && draft.anchors.size() > 1) index = nearestSegment(p) + 1;
        draft.anchors.add(index, new RouteDraft.Anchor(p)); selected = index; edited();
    }
    private int nearestSegment(RunRoute.Point p) {
        PointF at = map.getProjection().toScreenLocation(latLng(p)); double best = Double.MAX_VALUE; int index = 0;
        for (int i = 0; i < draft.segmentCount(); i++) {
            RunRoute.Point a = draft.anchors.get(i).point, b = draft.anchors.get((i + 1) % draft.anchors.size()).point;
            List<RunRoute.Point> shape = cache.get(WalkingRoutePlanner.key(endpoint(), a, b));
            if (shape == null || draft.mode != RouteDraft.Mode.ROAD) shape = Arrays.asList(a, b);
            for (int j = 1; j < shape.size(); j++) {
                PointF from = map.getProjection().toScreenLocation(latLng(shape.get(j - 1))), to = map.getProjection().toScreenLocation(latLng(shape.get(j)));
                double distance = RouteGeometry.segmentDistance(new double[] {at.x, at.y}, new double[] {from.x, from.y}, new double[] {to.x, to.y});
                if (distance < best) { best = distance; index = i; }
            }
        }
        return index;
    }
    private void deletePoint() {
        if (selected < 0 || selected >= draft.anchors.size() || draft.mode == RouteDraft.Mode.TRACK || saving) return;
        remember(); draft.anchors.remove(selected); selected = -1; edited();
    }
    private void toggleSegment() {
        if (draft.mode != RouteDraft.Mode.ROAD || selected < 0 || saving) return;
        if (selected == 0 && !draft.closed) { message("请选择路段终点；起点没有上一段"); return; }
        remember();
        if (selected == 0) draft.closingManual = !draft.closingManual;
        else draft.anchors.get(selected).manual = !draft.anchors.get(selected).manual;
        edited();
    }
    @Override public void selected(int index) { if (!saving) { selected = index; render(); } }
    @Override public void dragStarted(int index) { if (!saving) { remember(); selected = index; cancelPlanning(); } }
    @Override public void dragged(int index, RunRoute.Point p) {
        if (saving) return;
        if (draft.mode == RouteDraft.Mode.TRACK) {
            if (index == 0) draft.center = p;
            else if (index == 3) updateStart(p);
            else {
                double[] xy = new RouteGeometry.Plane(draft.center).xy(p);
                double radius = draft.radius + (draft.lane - 1) * draft.laneWidth;
                if (index == 1) {
                    draft.bearing = (Math.toDegrees(Math.atan2(xy[0], xy[1])) + 360) % 360;
                    draft.straight = Math.max(1, Math.min(2000, 2 * (Math.hypot(xy[0], xy[1]) - radius)));
                } else {
                    double a = Math.toRadians(draft.bearing);
                    draft.radius = Math.max(2, Math.min(480, Math.abs(xy[0] * Math.cos(a) - xy[1] * Math.sin(a)) - (draft.lane - 1) * draft.laneWidth));
                }
            }
        } else if (index < draft.anchors.size()) draft.anchors.get(index).point = p;
        geometryDirty = true;
        if (!dragRenderQueued) { dragRenderQueued = true; main.postDelayed(dragRender, 24); }
    }
    @Override public void dragEnded(int index) {
        main.removeCallbacks(dragRender); dragRenderQueued = false; if (!saving) edited();
    }
    private void updateStart(RunRoute.Point p) {
        try {
            RouteDraft base = RouteDraft.fromJson(draft.json()); base.startFraction = 0; base.clockwise = false;
            RunRoute shape = new RunRoute("track", RouteGeometry.track(base)); int best = 0; double distance = Double.MAX_VALUE;
            for (int i = 0; i < shape.size(); i++) { double d = RunRoute.distance(p, shape.get(i)); if (d < distance) { distance = d; best = i; } }
            draft.startFraction = shape.distanceAt(best) / shape.lengthMeters;
        } catch (Exception e) { message(e.getMessage()); }
    }

    private void render() {
        if (status == null || destroyed) return;
        boolean updateGeometry = geometryDirty;
        if (updateGeometry) { assembly = RouteAssembly.build(draft, cache, endpoint()); geometryDirty = false; }
        undoButton.setEnabled(!undo.isEmpty() && !saving); redoButton.setEnabled(!redo.isEmpty() && !saving);
        track.setEnabled(!saving); start.setEnabled(!saving);
        boolean isTrack = draft.mode == RouteDraft.Mode.TRACK;
        binding = true;
        inputModes.check(inputMode == InputMode.DRAW ? R.id.editor_input_draw : inputMode == InputMode.MOVE ? R.id.editor_input_move : R.id.editor_input_point);
        for (int i = 0; i < inputModes.getChildCount(); i++) {
            MaterialButton choice = (MaterialButton) inputModes.getChildAt(i);
            choice.setEnabled(!loading && !saving);
            choice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.color(this, choice.isChecked() ? R.color.primary_brand : R.color.ui_tonal)));
            choice.setTextColor(Ui.color(this, choice.isChecked() ? R.color.on_primary_brand : R.color.ui_accent));
            choice.setStrokeWidth(dp(choice.isChecked() ? 2 : 1));
        }
        binding = false;
        inputModes.setVisibility(isTrack ? View.GONE : View.VISIBLE);
        drawing.setVisibility(!isTrack && inputMode == InputMode.DRAW ? View.VISIBLE : View.GONE);
        drawing.setEnabled(!loading && !saving);
        overlay.setEnabled(!saving && (isTrack || inputMode == InputMode.POINT));
        closed.setVisibility(isTrack ? View.GONE : View.VISIBLE);
        smooth.setVisibility(draft.mode == RouteDraft.Mode.MANUAL ? View.VISIBLE : View.GONE);
        retry.setVisibility(draft.mode == RouteDraft.Mode.ROAD ? View.VISIBLE : View.GONE);
        retry.setEnabled(!loading && !saving);
        if (!wideTools) {
            LinearLayout.LayoutParams size = (LinearLayout.LayoutParams) save.getLayoutParams();
            size.width = isTrack ? 0 : dp(108); size.weight = isTrack ? 1 : 0; save.setLayoutParams(size);
        }
        track.setVisibility(isTrack ? View.VISIBLE : View.GONE); start.setVisibility(isTrack ? View.VISIBLE : View.GONE);
        delete.setVisibility(isTrack ? View.GONE : View.VISIBLE); segment.setVisibility(draft.mode == RouteDraft.Mode.ROAD ? View.VISIBLE : View.GONE);
        delete.setEnabled(selected >= 0 && !saving); segment.setEnabled(selected >= 0 && (selected > 0 || draft.closed) && !saving);
        if (draft.mode == RouteDraft.Mode.ROAD && selected >= 0 && selected < draft.anchors.size())
            segment.setText((selected == 0 ? draft.closingManual : draft.anchors.get(selected).manual) ? "本段沿路" : "本段手绘");
        start.setText(pickingStart ? "取消选起点" : "设置起跑点");
        hint.setText(isTrack ? pickingStart ? "点击跑道附近，设为起跑点" : "点击放置中心；拖“中”移动，拖“长”转向调长，拖“宽”调弯道"
                : inputMode == InputMode.MOVE ? "拖动移动地图，双指缩放；切回点选或画线继续编辑"
                : inputMode == InputMode.DRAW ? (draft.mode == RouteDraft.Mode.ROAD ? "拖动画线，松手沿道路规划；双指移动地图" : "拖动直接画轨迹，双指移动地图；下一笔从终点接续")
                : "点击加点 · 拖动调整 · 长按插点" + (draft.mode == RouteDraft.Mode.MANUAL ? "；可开启平滑曲线" : "；选择终点可切换本段手绘"));
        String text = loading ? "正在恢复草稿…" : assembly.route != null
                ? String.format(Locale.CHINA, "%s  ·  %.2f 米  ·  %d 点%s", isTrack ? "操场单圈" : draft.mode == RouteDraft.Mode.ROAD ? "步行路线" : "手动路线",
                assembly.route.lengthMeters, assembly.route.size(), assembly.route.closed ? "  ·  闭合循环" : "  ·  到终点折返") : assembly.problem;
        if (assembly.maxSnap > 20 && assembly.route != null) text += String.format(Locale.CHINA, "\n选点吸附到道路，最大偏移 %.0f 米", assembly.maxSnap);
        if (planning) text += "\n正在规划步行路段…";
        if (!requestError.isEmpty()) text += "\n" + requestError;
        status.setText(text); save.setText(saving ? "正在保存…" : "保存并应用");
        save.setEnabled(!loading && !saving && !planning && assembly.route != null);
        overlay.handles.clear(); overlay.pending.clear(); overlay.pending.addAll(assembly.pending); overlay.selected = selected;
        if (isTrack) {
            double r = draft.radius + (draft.lane - 1) * draft.laneWidth;
            overlay.handles.add(new RouteHandleOverlay.Handle(draft.center, "中"));
            overlay.handles.add(new RouteHandleOverlay.Handle(RouteGeometry.offset(draft.center, draft.straight / 2 + r, 0, draft.bearing), "长"));
            overlay.handles.add(new RouteHandleOverlay.Handle(RouteGeometry.offset(draft.center, 0, r, draft.bearing), "宽"));
            if (assembly.route != null) overlay.handles.add(new RouteHandleOverlay.Handle(assembly.route.get(0), "起"));
        } else for (int i = 0; i < draft.anchors.size(); i++) overlay.handles.add(new RouteHandleOverlay.Handle(draft.anchors.get(i).point, String.valueOf(i + 1)));
        if (updateGeometry) drawLines(); else overlay.reprojectHandles();
    }
    private void drawLines() {
        overlay.routes.clear(); overlay.routes.addAll(assembly.pieces); overlay.reproject();
    }

    private void cancelPlanning() {
        revision++; if (planningTask != null) planningTask.cancel(true); planner.cancel(); planning = false;
    }
    private void plan() {
        cancelPlanning(); geometryDirty = true;
        if (loading || saving || draft.mode != RouteDraft.Mode.ROAD || destroyed) { render(); return; }
        final int expected = revision; final String service = endpoint();
        List<RunRoute.Point[]> missing = new ArrayList<>();
        for (int i = 0; i < draft.segmentCount(); i++) {
            RunRoute.Point a = draft.anchors.get(i).point, b = draft.anchors.get((i + 1) % draft.anchors.size()).point;
            if (!draft.manualSegment(i) && !cache.containsKey(WalkingRoutePlanner.key(service, a, b))) missing.add(new RunRoute.Point[] {a, b});
        }
        if (missing.isEmpty()) { render(); return; }
        planning = true; render();
        planningTask = network.submit(() -> {
            for (RunRoute.Point[] leg : missing) {
                if (Thread.currentThread().isInterrupted()) return;
                try {
                    List<RunRoute.Point> shape = planner.route(service, leg[0], leg[1]);
                    main.post(() -> { if (!destroyed && revision == expected) { cache.put(WalkingRoutePlanner.key(service, leg[0], leg[1]), shape); geometryDirty = true; render(); schedulePersist(); } });
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String error = e instanceof java.net.SocketTimeoutException ? "路线服务超时，可重试或使用手绘段" : e.getMessage();
                    main.post(() -> { if (!destroyed && revision == expected) { requestError = error == null ? "网络不可用，请重试" : error; render(); } });
                }
            }
            main.post(() -> { if (!destroyed && revision == expected) { planning = false; render(); schedulePersist(); } });
        });
    }

    private void loadStyle() {
        if (realMapStyle != null) realMapStyle.load();
    }
    private void restoreCamera() {
        if (map == null) return;
        RunRoute.Point center = Double.isFinite(cameraLat) ? new RunRoute.Point(cameraLat, cameraLon) : draft.center;
        map.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(latLng(center)).zoom(cameraZoom).build()));
        if (!Double.isFinite(cameraLat) && (draft.mode == RouteDraft.Mode.TRACK || !draft.anchors.isEmpty())) fit();
    }
    private void zoom(int by) { if (map != null) map.animateCamera(CameraUpdateFactory.zoomBy(by)); }
    private void fit() {
        if (map == null) return;
        List<RunRoute.Point> points = draft.mode == RouteDraft.Mode.TRACK && assembly.route != null ? new ArrayList<>() : draft.points();
        if (draft.mode == RouteDraft.Mode.TRACK && assembly.route != null) for (int i = 0; i < assembly.route.size(); i++) points.add(assembly.route.get(i));
        if (points.isEmpty()) { map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng(draft.center), 17)); return; }
        if (points.size() == 1) { map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng(points.get(0)), 17)); return; }
        LatLngBounds.Builder bounds = new LatLngBounds.Builder(); for (RunRoute.Point p : points) bounds.include(latLng(p));
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), dp(55)));
    }
    @SuppressWarnings("MissingPermission") private void locate() {
        if (map == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION); return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE); Location newest = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && (newest == null || location.getTime() > newest.getTime())) newest = location;
            }
        } catch (SecurityException ignored) { }
        if (newest == null) message("暂未获得定位，可在“更多 → 跳转位置”输入坐标");
        else map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(newest.getLatitude(), newest.getLongitude()), 17));
    }

    private void trackParameters() {
        if (saving) return;
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0);
        TextInputLayout straight = number(form, "单段直道长度（米）", draft.straight), radius = number(form, "第 1 道中心线半径（米）", draft.radius),
                bearing = number(form, "朝向（度，0 为北）", draft.bearing), lane = number(form, "道次（1–9）", draft.lane), width = number(form, "道宽（米）", draft.laneWidth);
        MaterialSwitch clockwise = new MaterialSwitch(this); clockwise.setText("顺时针"); clockwise.setChecked(draft.clockwise); form.addView(clockwise);
        Ui.add(form, Ui.note(this, "单圈 = 2 × 直道 + 2π × 当前道半径。模板长度是计算值，需对照实际操场校准。"), 8);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("跑道参数").setView(scroll)
                .setNeutralButton("400 米模板", (d, w) -> { remember(); draft.radius = 36.8; draft.straight = (400 - 2 * Math.PI * 36.8) / 2; draft.lane = 1; draft.laneWidth = 1.22; edited(); fit(); })
                .setNegativeButton("取消", null).setPositiveButton("应用", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(button -> {
            try {
                RouteDraft next = RouteDraft.fromJson(draft.json()); next.straight = decimal(straight); next.radius = decimal(radius);
                next.bearing = decimal(bearing); double laneValue = decimal(lane); if (laneValue != Math.rint(laneValue)) throw new IllegalArgumentException("道次须为整数");
                next.lane = (int) laneValue; next.laneWidth = decimal(width); next.clockwise = clockwise.isChecked();
                RouteGeometry.track(next); remember(); draft = next; edited(); fit(); dialog.dismiss();
            } catch (Exception e) { message(e.getMessage()); }
        })); dialog.show();
    }
    private TextInputLayout number(LinearLayout parent, String label, double value) {
        TextInputLayout field = Ui.field(this, label, String.format(Locale.US, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", ""),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED); Ui.add(parent, field, 8); return field;
    }
    private double decimal(TextInputLayout field) { double value = Double.parseDouble(Ui.value(field)); if (!Double.isFinite(value)) throw new IllegalArgumentException("请输入有效数值"); return value; }

    private void more() {
        if (saving || loading) return;
        String[] options = {"新建道路路线", "新建操场", "新建手动路线", "从当前路线编辑", "跳转位置", "选点列表", "地图与路线服务", "重试地图", "恢复上次草稿", "操作说明"};
        new MaterialAlertDialogBuilder(this).setTitle("路线工具").setItems(options, (dialog, which) -> {
            if (which < 3) newDraft(RouteDraft.Mode.values()[which]);
            else if (which == 3) fromCurrent(); else if (which == 4) jump(); else if (which == 5) pointList();
            else if (which == 6) services(); else if (which == 7) loadStyle(); else if (which == 8) restorePreviousDraft();
            else new MaterialAlertDialogBuilder(this).setTitle("地图绘制说明")
                    .setMessage("点选：点击加点，长按插点，拖动小圆点调整。小圆点周围保留了更大的触摸范围。\n\n画线：单指或鼠标拖动直接绘制，松手结束一笔；下一笔从终点附近接续。双指移动或缩放地图，也可切到“移动”后单指拖图。每笔可撤销和重做。\n\n道路：点选或画线后按步行路网规划；红色虚线表示未完成部分。选中终点可将该段改为手绘。\n\n操场：拖动中心、长度和宽度手柄，参数中可切换方向与道次。\n\n手动：保留绘制形状，可选平滑和闭合；平滑不保证沿道路。手机下方“工具”可展开更多操作。\n\n草稿自动保存在本机；只有“保存并应用”会替换当前路线。")
                    .setPositiveButton("知道了", null).show();
        }).show();
    }
    private void restorePreviousDraft() {
        if (recoverableDraft == null) { message("暂无可恢复的草稿"); return; }
        try {
            RouteDraft restored = RouteDraft.fromJson(recoverableDraft.getJSONObject("draft"));
            remember(); draft = restored; modeDrafts.put(draft.mode, draft); selected = -1;
            restoreCacheAndCamera(recoverableDraft); edited(); restoreCamera();
        } catch (Exception e) { message("上次草稿无法恢复，原文件仍保留"); }
    }
    private void searchPlaces() {
        if (saving || loading) return;
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0);
        TextInputLayout query = Ui.field(this, "搜索地点或地址", "", InputType.TYPE_CLASS_TEXT);
        query.getEditText().setId(R.id.place_search_input); query.getEditText().setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        Ui.add(form, query, 8); TextView state = Ui.note(this, "输入地名后点击搜索，选择结果跳转地图"); Ui.add(form, state, 8);
        ScrollView scroll = new ScrollView(this); LinearLayout results = Ui.column(this); scroll.addView(results); form.addView(scroll, new LinearLayout.LayoutParams(-1, dp(240)));
        Ui.add(form, Ui.note(this, "搜索数据 © OpenStreetMap contributors"), 6);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("搜索位置").setView(form)
                .setNegativeButton("关闭", null).setNeutralButton("输入坐标", (d, w) -> jump()).setPositiveButton("搜索", null).create();
        Runnable submit = () -> {
            String text = Ui.value(query);
            if (text.length() < 2 || text.length() > 120) { query.setError("请输入 2–120 字的地点或地址"); return; }
            query.setError(null); int expected = ++searchRevision;
            if (searchTask != null) searchTask.cancel(true); placeSearch.cancel();
            results.removeAllViews(); state.setText("正在搜索…");
            String service = prefs.getString("search_endpoint", PlaceSearch.DEFAULT_ENDPOINT);
            searchTask = searchWorker.submit(() -> {
                try {
                    List<PlaceSearch.Result> matches = placeSearch.search(service, text);
                    main.post(() -> {
                        if (destroyed || expected != searchRevision || !dialog.isShowing()) return;
                        state.setText(matches.isEmpty() ? "未找到地点，可补充城市名后重试" : "选择地点跳转，当前路线保持不变");
                        for (PlaceSearch.Result result : matches) {
                            MaterialButton item = button(result.name + "\n" + result.address, v -> {
                                if (map != null) {
                                    map.cancelTransitions();
                                    if (result.north - result.south > .00001 && result.east - result.west > .00001 && result.north - result.south < 10 && result.east - result.west < 10)
                                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds.Builder().include(new LatLng(result.south, result.west)).include(new LatLng(result.north, result.east)).build(), dp(36)));
                                    else map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng(result.point), 16));
                                }
                                dialog.dismiss();
                            });
                            item.setSingleLine(false); item.setMaxLines(3); item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); Ui.add(results, item, 6);
                        }
                    });
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) return;
                    main.post(() -> { if (!destroyed && expected == searchRevision && dialog.isShowing()) state.setText("搜索暂不可用，请检查网络、重试或输入坐标跳转"); });
                }
            });
        };
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> submit.run()));
        query.getEditText().setOnEditorActionListener((v, action, event) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { submit.run(); return true; } return false; });
        dialog.setOnDismissListener(v -> { searchRevision++; if (searchTask != null) searchTask.cancel(true); placeSearch.cancel(); }); dialog.show();
    }
    private void newDraft(RouteDraft.Mode mode) {
        new MaterialAlertDialogBuilder(this).setTitle("新建路线？").setMessage("可以通过撤销恢复当前编辑。")
                .setNegativeButton("取消", null).setPositiveButton("新建", (dialog, which) -> {
                    remember(); RouteDraft next = new RouteDraft(); next.mode = mode; next.name = mode == RouteDraft.Mode.TRACK ? "操场路线" : "地图路线";
                    next.center = map == null ? draft.center : point(map.getCameraPosition().target);
                    if (mode == RouteDraft.Mode.TRACK) initializeTrack(next);
                    draft = next; selected = -1; pickingStart = false; edited(); fit();
                }).show();
    }
    private void initializeTrack(RouteDraft target) {
        RunRoute.Point camera = map == null ? target.center : point(map.getCameraPosition().target);
        if (currentTrack != null && RunRoute.distance(camera, currentTrack.center) < 500) {
            target.center = currentTrack.center; target.radius = currentTrack.radius; target.straight = currentTrack.straight;
            target.bearing = currentTrack.bearing; target.startFraction = currentTrack.startFraction;
            target.lane = 1; target.laneWidth = currentTrack.laneWidth;
        } else target.center = camera;
        target.trackInitialized = true;
    }
    private void fromCurrent() {
        storage.execute(() -> {
            try (InputStream in = getResources().openRawResource(R.raw.dutpj_mutipoint)) {
                RouteDraft next = RouteDraft.fromRoute(repository.load(in, getSharedPreferences("workspace", MODE_PRIVATE).getString("route_name", "自定义路线")));
                main.post(() -> { if (!destroyed && !saving) { remember(); draft = next; selected = -1; edited(); fit(); } });
            } catch (Exception e) { main.post(() -> { if (!destroyed) message(e.getMessage()); }); }
        });
    }
    private void jump() {
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0);
        RunRoute.Point center = map == null ? draft.center : point(map.getCameraPosition().target);
        TextInputLayout lat = number(form, "纬度（WGS84）", center.latitude), lon = number(form, "经度（WGS84）", center.longitude);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("跳转位置").setView(form)
                .setNegativeButton("取消", null).setPositiveButton("跳转", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try { RunRoute.Point p = new RunRoute.Point(decimal(lat), decimal(lon)); if (Math.abs(p.latitude) > 85) throw new IllegalArgumentException("地图纬度范围为 ±85°");
                if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng(p), 17)); dialog.dismiss(); }
            catch (Exception e) { message("请输入有效的 WGS84 经纬度"); }
        })); dialog.show();
    }
    private void pointList() {
        if (draft.mode == RouteDraft.Mode.TRACK) { trackParameters(); return; }
        if (draft.anchors.isEmpty()) { message("尚未选点"); return; }
        String[] items = new String[draft.anchors.size()];
        for (int i = 0; i < items.length; i++) { RunRoute.Point p = draft.anchors.get(i).point; items[i] = String.format(Locale.CHINA, "%d  ·  %.6f, %.6f", i + 1, p.latitude, p.longitude); }
        new MaterialAlertDialogBuilder(this).setTitle("选择控制点").setItems(items, (dialog, which) -> {
            selected = which; render(); if (map != null) map.animateCamera(CameraUpdateFactory.newLatLng(latLng(draft.anchors.get(which).point)));
        }).show();
    }
    private void services() {
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0);
        TextInputLayout route = Ui.field(this, "Valhalla 步行接口（HTTPS）", endpoint(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextInputLayout style = Ui.field(this, "地图样式 HTTPS 地址，或填写 osm", prefs.getString("style", DEFAULT_STYLE), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextInputLayout search = Ui.field(this, "地点搜索服务（Photon / Nominatim HTTPS）", prefs.getString("search_endpoint", PlaceSearch.DEFAULT_ENDPOINT), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        Ui.add(form, route, 8); Ui.add(form, style, 8); Ui.add(form, search, 8);
        Ui.add(form, Ui.note(this, "默认使用 OpenFreeMap 底图及 FOSSGIS 公共步行服务，适合小量测试。道路选点会发送到所选路线服务；地图与路线可能受网络及覆盖影响。"), 12);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("地图与路线服务").setView(scroll)
                .setNeutralButton("恢复默认", (d, w) -> { prefs.edit().remove("endpoint").remove("style").remove("search_endpoint").apply(); requestError = ""; loadStyle(); plan(); })
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            try { WalkingRoutePlanner.validateEndpoint(Ui.value(route)); if (!"osm".equals(Ui.value(style))) WalkingRoutePlanner.validateEndpoint(Ui.value(style)); WalkingRoutePlanner.validateEndpoint(Ui.value(search));
                prefs.edit().putString("endpoint", Ui.value(route)).putString("style", Ui.value(style)).putString("search_endpoint", Ui.value(search)).apply(); requestError = ""; loadStyle(); plan(); dialog.dismiss(); }
            catch (Exception e) { message("请填写有效 HTTPS 地址；地图样式也可填写 osm"); }
        })); dialog.show();
    }

    private void nameAndSave() {
        if (assembly.route == null || planning || saving || loading) return;
        if (LocationMockService.latest.active()) { message("请先结束正在运行的路线"); return; }
        TextInputLayout name = Ui.field(this, "路线名称", draft.name, InputType.TYPE_CLASS_TEXT);
        LinearLayout form = Ui.column(this); form.setPadding(dp(20), 0, dp(20), 0); Ui.add(form, name, 8);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("保存并应用路线").setView(form)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(-1).setOnClickListener(b -> {
            String value = Ui.value(name); if (value.isEmpty() || value.length() > 80) { name.setError("名称请填写 1–80 个字"); return; }
            if (LocationMockService.latest.active()) { message("请先结束正在运行的路线"); return; }
            draft.name = value; assembly = RouteAssembly.build(draft, cache, endpoint()); if (assembly.route == null) { message(assembly.problem); return; }
            saving = true; overlay.setEnabled(false); back.setEnabled(false); modes.setEnabled(false); syncControls(); render(); dialog.dismiss();
            try {
                RouteDraft snapshot = RouteDraft.fromJson(draft.json()); RunRoute route = assembly.route; JSONObject data = snapshot();
                storage.execute(() -> {
                    try {
                        repository.saveAndSelect(route, snapshot);
                        try { data.put("draft", snapshot.json()); repository.saveDraft(data); } catch (Exception ignored) { }
                        main.post(() -> { if (!destroyed) { setResult(RESULT_OK, new Intent().putExtra("route_name", route.name)); finish(); } });
                    } catch (Exception e) { main.post(() -> { if (!destroyed) { saving = false; overlay.setEnabled(true); back.setEnabled(true); modes.setEnabled(true); syncControls(); render(); message("保存失败：" + e.getMessage()); } }); }
                });
            } catch (Exception e) { saving = false; overlay.setEnabled(true); back.setEnabled(true); modes.setEnabled(true); syncControls(); render(); message(e.getMessage()); }
        })); dialog.show();
    }
    private JSONObject snapshot() throws Exception {
        JSONObject data = new JSONObject().put("draft", draft.json()); JSONArray saved = new JSONArray();
        int points = 0;
        List<Map.Entry<String, List<RunRoute.Point>>> newest = new ArrayList<>(cache.entrySet()); Collections.reverse(newest);
        for (Map.Entry<String, List<RunRoute.Point>> entry : newest) {
            if (points + entry.getValue().size() > 50000) continue;
            saved.put(new JSONObject().put("key", entry.getKey()).put("points", RouteDraft.encodePoints(entry.getValue()))); points += entry.getValue().size();
        }
        data.put("cache", saved);
        if (map != null && map.getCameraPosition().target != null) {
            CameraPosition camera = map.getCameraPosition(); data.put("lat", camera.target.getLatitude()).put("lon", camera.target.getLongitude()).put("zoom", camera.zoom);
        }
        return data;
    }
    private void restoreCacheAndCamera(JSONObject data) {
        JSONArray stored = data.optJSONArray("cache");
        if (stored != null) for (int i = 0; i < Math.min(60, stored.length()); i++) {
            try { JSONObject entry = stored.getJSONObject(i); List<RunRoute.Point> shape = RouteDraft.decodePoints(entry.getJSONArray("points"));
                if (shape.size() >= 2) cache.put(entry.getString("key"), shape); } catch (Exception ignored) { }
        }
        double lat = data.optDouble("lat", Double.NaN), lon = data.optDouble("lon", 0), zoom = data.optDouble("zoom", 17);
        if (Double.isFinite(lat) && Math.abs(lat) <= 85 && Double.isFinite(lon) && Math.abs(lon) <= 180) {
            cameraLat = lat; cameraLon = lon; cameraZoom = Double.isFinite(zoom) ? Math.max(2, Math.min(20, zoom)) : 17;
        }
    }
    private void schedulePersist() { if (!loading && !saving && !destroyed) { main.removeCallbacks(autoSave); main.postDelayed(autoSave, 450); } }
    private void persist() {
        if (loading || saving || destroyed || !draftTouched) return;
        try { JSONObject data = snapshot(); storage.execute(() -> { try { repository.saveDraft(data); }
            catch (Exception e) { main.post(() -> { if (!destroyed) message("草稿保存失败，请检查可用空间"); }); } }); }
        catch (Exception e) { message("草稿暂时无法保存"); }
    }
    private void leave() { if (!saving) { persist(); setResult(RESULT_CANCELED); finish(); } }
    private void message(String text) { if (!destroyed) Toast.makeText(this, text == null ? "操作失败" : text, Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { persist(); mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); mapView.onSaveInstanceState(state);
        if (!loading) try { state.putString("editor_draft", draft.json().toString()); } catch (Exception ignored) { }
        if (map != null && map.getCameraPosition().target != null) {
            CameraPosition c = map.getCameraPosition(); state.putDouble("editor_lat", c.target.getLatitude()); state.putDouble("editor_lon", c.target.getLongitude()); state.putDouble("editor_zoom", c.zoom);
        }
        persist();
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null); destroyed = true; cancelPlanning(); network.shutdownNow(); storage.shutdown();
        searchRevision++; if (searchTask != null) searchTask.cancel(true); if (placeSearch != null) placeSearch.cancel(); searchWorker.shutdownNow();
        if (realMapStyle != null) realMapStyle.dispose(); mapView.onDestroy(); super.onDestroy();
    }
}
