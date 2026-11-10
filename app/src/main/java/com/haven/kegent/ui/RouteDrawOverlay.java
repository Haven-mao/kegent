package com.haven.kegent.ui;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import com.haven.kegent.R;
import com.haven.kegent.core.RunRoute;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import java.util.ArrayList;
import java.util.List;

/** Single finger/mouse draws; two fingers hand off to native pan/zoom without committing a stroke. */
public final class RouteDrawOverlay extends View {
    public interface Listener {
        void strokeStarted();
        void strokeFinished(List<RunRoute.Point> points, double tolerance);
        void strokeCancelled();
    }
    private final MapView mapView;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ink = new Path();
    private final List<PointF> points = new ArrayList<>();
    public MapLibreMap map;
    public Listener listener;
    private float lastX, lastY;
    private boolean active, mapGesture, tooLong;
    public RouteDrawOverlay(Context context, MapView mapView) {
        super(context); this.mapView = mapView;
        setContentDescription("自由绘制区域：单指或鼠标拖动画线，双指移动或缩放地图");
        setVisibility(GONE);
    }
    public void cancelStroke() {
        boolean hadStroke = active; active = false; mapGesture = false; points.clear(); ink.reset(); invalidate();
        if (hadStroke && listener != null) listener.strokeCancelled();
    }
    @Override protected void onDraw(Canvas canvas) {
        if (!active || mapGesture) return;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Ui.dp(getContext(), 6)); paint.setColor(Ui.color(getContext(), R.color.ui_surface)); canvas.drawPath(ink, paint);
        paint.setStrokeWidth(Ui.dp(getContext(), 3.5f)); paint.setColor(Ui.color(getContext(), R.color.ui_accent)); canvas.drawPath(ink, paint);
    }
    private void sample(float x, float y, boolean force) {
        if (!force && Math.hypot(x - lastX, y - lastY) < Ui.dp(getContext(), 2)) return;
        if (points.size() >= 10000) { tooLong = true; return; }
        if (points.isEmpty()) ink.moveTo(x, y); else ink.lineTo(x, y);
        points.add(new PointF(x, y)); lastX = x; lastY = y;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || map == null || listener == null) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            map.cancelTransitions(); points.clear(); ink.reset(); active = true; mapGesture = false; tooLong = false;
            getParent().requestDisallowInterceptTouchEvent(true); listener.strokeStarted();
            sample(event.getX(), event.getY(), true); invalidate(); return true;
        }
        if (!active && !mapGesture) return false;
        if (event.getPointerCount() > 1 && !mapGesture) {
            active = false; mapGesture = true; points.clear(); ink.reset(); invalidate(); listener.strokeCancelled();
            MotionEvent down = MotionEvent.obtain(event.getDownTime(), event.getEventTime(), MotionEvent.ACTION_DOWN,
                    event.getX(0), event.getY(0), event.getMetaState());
            mapView.onTouchEvent(down); down.recycle();
        }
        if (mapGesture) {
            mapView.onTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mapGesture = false; getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getHistorySize(); i++) sample(event.getHistoricalX(i), event.getHistoricalY(i), false);
            sample(event.getX(), event.getY(), false); postInvalidateOnAnimation(); return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            sample(event.getX(), event.getY(), true); active = false; ink.reset(); invalidate(); performClick();
            LatLng left = map.getProjection().fromScreenLocation(new PointF(event.getX(), event.getY()));
            LatLng right = map.getProjection().fromScreenLocation(new PointF(event.getX() + Ui.dp(getContext(), 2), event.getY()));
            double tolerance = Math.max(.5, RunRoute.distance(new RunRoute.Point(left.getLatitude(), left.getLongitude()), new RunRoute.Point(right.getLatitude(), right.getLongitude())));
            double[] pixels = new double[points.size() * 2], coordinates = new double[pixels.length];
            for (int i = 0; i < points.size(); i++) { pixels[i * 2] = points.get(i).x; pixels[i * 2 + 1] = points.get(i).y; }
            if (!points.isEmpty()) map.getProjection().fromScreenLocations(pixels, coordinates);
            List<RunRoute.Point> result = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) result.add(new RunRoute.Point(coordinates[i * 2], coordinates[i * 2 + 1])); points.clear();
            getParent().requestDisallowInterceptTouchEvent(false);
            if (tooLong) { android.widget.Toast.makeText(getContext(), "这一笔过长，请分段绘制", android.widget.Toast.LENGTH_LONG).show(); listener.strokeCancelled(); }
            else listener.strokeFinished(result, tolerance);
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            cancelStroke(); getParent().requestDisallowInterceptTouchEvent(false); return true;
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
