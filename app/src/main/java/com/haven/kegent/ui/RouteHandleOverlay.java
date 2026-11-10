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
        if (map == null) return;
        routePaths.clear(); pendingPaths.clear(); handlePositions.clear();
        buildPaths(routes, routePaths); buildPaths(pending, pendingPaths);
        for (Handle handle : handles) handlePositions.add(project(handle.point));
        invalidate();
    }
    private void buildPaths(List<List<RunRoute.Point>> shapes, List<Path> paths) {
        for (List<RunRoute.Point> shape : shapes) {
            Path path = new Path(); boolean first = true;
            for (RunRoute.Point point : shape) {
                PointF at = project(point);
                if (first) { path.moveTo(at.x, at.y); first = false; } else path.lineTo(at.x, at.y);
            }
            paths.add(path);
        }
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
            canvas.drawCircle(at.x, at.y, dp(i == selected ? 15 : 12), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(i == selected ? 3 : 2));
            paint.setColor(Ui.color(getContext(), R.color.ui_accent)); canvas.drawCircle(at.x, at.y, dp(i == selected ? 15 : 12), paint);
            paint.setStyle(Paint.Style.FILL); paint.setTextSize(dp(11)); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(h.label, at.x, at.y - (paint.ascent() + paint.descent()) / 2, paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (map == null || listener == null || !isEnabled()) return false;
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressed = -1; double best = dp(26);
            for (int i = 0; i < handles.size(); i++) {
                RunRoute.Point p = handles.get(i).point;
                PointF at = map.getProjection().toScreenLocation(new LatLng(p.latitude, p.longitude));
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
}
