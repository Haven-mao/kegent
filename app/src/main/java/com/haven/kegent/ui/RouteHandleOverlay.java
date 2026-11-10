package com.haven.kegent.ui;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import com.haven.kegent.R;
import com.haven.kegent.core.RunRoute;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import java.util.ArrayList;
import java.util.List;

/** Only handle touches are intercepted; all other gestures go directly to the native map. */
public final class RouteHandleOverlay extends View {
    public static final class Handle {
        public final RunRoute.Point point; public final String label;
        public Handle(RunRoute.Point point, String label) { this.point = point; this.label = label; }
    }
    public interface Listener {
        void selected(int index);
        void dragStarted(int index);
        void dragged(int index, RunRoute.Point point);
        void dragEnded(int index);
    }
    public MapLibreMap map;
    public final List<Handle> handles = new ArrayList<>();
    public final List<List<RunRoute.Point>> pending = new ArrayList<>();
    public final List<List<RunRoute.Point>> routes = new ArrayList<>();
    public int selected = -1;
    public Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Path> routePaths = new ArrayList<>(), pendingPaths = new ArrayList<>();
    private final List<PointF> handlePositions = new ArrayList<>();
    private final LatLng coordinate = new LatLng();
    private double[] input = new double[0], output = new double[0];
    private double[] handleInput = new double[0], handleOutput = new double[0];
    private boolean queued, geometryDirty = true;
    private final Runnable projection = this::projectFrame;
    private final DashPathEffect dashed;
    private int pressed = -1;
    private float downX, downY;
    private boolean dragging;
    public RouteHandleOverlay(Context context) {
        super(context); dashed = new DashPathEffect(new float[] {dp(7), dp(5)}, 0);
        setContentDescription("路线控制点，可拖动调整位置");
    }
    private float dp(float value) { return Ui.dp(getContext(), value); }
    private PointF project(RunRoute.Point point) {
        coordinate.setLatitude(point.latitude); coordinate.setLongitude(point.longitude);
        return map.getProjection().toScreenLocation(coordinate);
    }
    public void reproject() {
        geometryDirty = true; requestProjection();
    }
    public void reprojectHandles() { requestProjection(); }
    private void requestProjection() {
        if (!queued) { queued = true; postOnAnimation(projection); }
    }
    private void projectFrame() {
        queued = false;
        if (map == null) return;
        if (!geometryDirty) {
            updateHandlePositions(); invalidate(); return;
        }
        geometryDirty = false;
        int count = handles.size();
        for (List<RunRoute.Point> shape : routes) count += shape.size();
        for (List<RunRoute.Point> shape : pending) count += shape.size();
        if (input.length != count * 2) { input = new double[count * 2]; output = new double[count * 2]; }
        int index = 0;
        for (List<RunRoute.Point> shape : routes) for (RunRoute.Point p : shape) { input[index++] = p.latitude; input[index++] = p.longitude; }
        for (List<RunRoute.Point> shape : pending) for (RunRoute.Point p : shape) { input[index++] = p.latitude; input[index++] = p.longitude; }
        for (Handle handle : handles) { input[index++] = handle.point.latitude; input[index++] = handle.point.longitude; }
        if (count > 0) map.getProjection().toScreenLocations(input, output);
        int offset = buildPaths(routes, routePaths, 0); offset = buildPaths(pending, pendingPaths, offset);
        while (handlePositions.size() > handles.size()) handlePositions.remove(handlePositions.size() - 1);
        for (int i = 0; i < handles.size(); i++) {
            if (i >= handlePositions.size()) handlePositions.add(new PointF());
            handlePositions.get(i).set((float) output[offset++], (float) output[offset++]);
        }
        invalidate();
    }
    private void updateHandlePositions() {
        if (handleInput.length != handles.size() * 2) { handleInput = new double[handles.size() * 2]; handleOutput = new double[handleInput.length]; }
        for (int i = 0; i < handles.size(); i++) { handleInput[i * 2] = handles.get(i).point.latitude; handleInput[i * 2 + 1] = handles.get(i).point.longitude; }
        if (!handles.isEmpty()) map.getProjection().toScreenLocations(handleInput, handleOutput);
        while (handlePositions.size() > handles.size()) handlePositions.remove(handlePositions.size() - 1);
        for (int i = 0; i < handles.size(); i++) {
            if (i >= handlePositions.size()) handlePositions.add(new PointF());
            handlePositions.get(i).set((float) handleOutput[i * 2], (float) handleOutput[i * 2 + 1]);
        }
    }
    private int buildPaths(List<List<RunRoute.Point>> shapes, List<Path> paths, int offset) {
        while (paths.size() > shapes.size()) paths.remove(paths.size() - 1);
        for (int i = 0; i < shapes.size(); i++) {
            if (i >= paths.size()) paths.add(new Path()); Path path = paths.get(i); path.rewind(); boolean first = true;
            for (int j = 0; j < shapes.get(i).size(); j++) {
                float x = (float) output[offset++], y = (float) output[offset++];
                if (first) { path.moveTo(x, y); first = false; } else path.lineTo(x, y);
            }
        }
        return offset;
    }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight); reproject();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (map == null) return;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(dp(6)); paint.setColor(Ui.color(getContext(), R.color.ui_surface));
        for (Path path : routePaths) canvas.drawPath(path, paint);
        paint.setStrokeWidth(dp(3.5f)); paint.setColor(Ui.color(getContext(), R.color.ui_accent));
        for (Path path : routePaths) canvas.drawPath(path, paint);
        paint.setStrokeWidth(dp(2)); paint.setColor(Ui.color(getContext(), R.color.ui_error)); paint.setPathEffect(dashed);
        for (Path path : pendingPaths) canvas.drawPath(path, paint);
        paint.setPathEffect(null);
        for (int i = 0; i < Math.min(handles.size(), handlePositions.size()); i++) {
            Handle h = handles.get(i); PointF at = handlePositions.get(i);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Ui.color(getContext(), R.color.ui_surface));
            canvas.drawCircle(at.x, at.y, dp(i == selected ? 8 : 6), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(i == selected ? 2 : 1.5f));
            paint.setColor(Ui.color(getContext(), R.color.ui_accent)); canvas.drawCircle(at.x, at.y, dp(i == selected ? 8 : 6), paint);
            if (i == selected || handles.size() <= 30) {
                paint.setStyle(Paint.Style.FILL); paint.setTextSize(dp(9)); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
                String label = h.label;
                if (label.length() > 2) {
                    paint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(label, at.x + dp(10), at.y - dp(7), paint);
                } else canvas.drawText(label, at.x, at.y - (paint.ascent() + paint.descent()) / 2, paint);
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (map == null || listener == null || !isEnabled()) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressed = -1; double best = dp(26);
            for (int i = 0; i < handles.size(); i++) {
                PointF at = i < handlePositions.size() ? handlePositions.get(i) : project(handles.get(i).point);
                double distance = Math.hypot(at.x - e.getX(), at.y - e.getY());
                if (distance < best) { best = distance; pressed = i; }
            }
            if (pressed < 0) return false;
            downX = e.getX(); downY = e.getY(); dragging = false;
            getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        if (pressed < 0) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
            if (!dragging && Math.hypot(e.getX() - downX, e.getY() - downY) > dp(4)) {
                dragging = true; listener.dragStarted(pressed);
            }
            if (dragging) {
                LatLng p = map.getProjection().fromScreenLocation(new PointF(e.getX(), e.getY()));
                listener.dragged(pressed, new RunRoute.Point(p.getLatitude(), p.getLongitude()));
            }
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (dragging) listener.dragEnded(pressed);
            else if (e.getActionMasked() == MotionEvent.ACTION_UP) { listener.selected(pressed); performClick(); }
            pressed = -1; dragging = false; getParent().requestDisallowInterceptTouchEvent(false); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDetachedFromWindow() { removeCallbacks(projection); queued = false; super.onDetachedFromWindow(); }
}
