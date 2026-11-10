package com.haven.kegent.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import com.haven.kegent.R;
import com.haven.kegent.core.RunRoute;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.*;
import java.util.ArrayList;
import java.util.List;

/** A real native map. Its route overlay remains available while street tiles load. */
public final class RoutePreviewView extends FrameLayout implements DefaultLifecycleObserver {
    private final MapView mapView;
    private final RouteHandleOverlay overlay;
    private final TextView notice;
    private final LifecycleOwner owner;
    private MapLibreMap map;
    private RealMapStyle style;
    private RunRoute route;
    private ValueAnimator movement;
    private double targetDistance, traveled;
    private boolean disposed, started, resumed;
    private String loadedStyle;

    public RoutePreviewView(Context context) {
        super(context); MapLibre.getInstance(context);
        setMinimumHeight(Ui.dp(context, 190));
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(Ui.color(context, R.color.ui_tonal)); shape.setCornerRadius(Ui.dp(context, 14)); setBackground(shape); setClipToOutline(true);
        mapView = new MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)
                .rotateGesturesEnabled(false).tiltGesturesEnabled(false).logoEnabled(false).compassEnabled(false));
        mapView.onCreate(null); addView(mapView, new FrameLayout.LayoutParams(-1, -1));
        overlay = new RouteHandleOverlay(context); overlay.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        notice = Ui.note(context, "正在加载真实地图…"); notice.setTextSize(11);
        notice.setPadding(Ui.dp(context, 8), Ui.dp(context, 4), Ui.dp(context, 8), Ui.dp(context, 4));
        notice.setBackgroundColor(Ui.color(context, R.color.ui_surface)); addView(notice, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        notice.setOnClickListener(v -> { if (style != null) style.load(); });
        owner = context instanceof LifecycleOwner ? (LifecycleOwner) context : null;
        if (owner != null) owner.getLifecycle().addObserver(this);
        mapView.getMapAsync(ready -> {
            if (disposed) return;
            map = ready; overlay.map = map;
            map.addOnCameraMoveListener(overlay::reproject); map.addOnCameraIdleListener(overlay::reproject);
            style = new RealMapStyle(context, mapView, map, notice, overlay::reproject); loadStyle(); fit();
        });
    }
    private void loadStyle() {
        loadedStyle = getContext().getSharedPreferences("route_editor", Context.MODE_PRIVATE).getString("style", RealMapStyle.DEFAULT_STYLE);
        style.load();
    }
    public void setRoute(RunRoute route) {
        cancelMovement(); this.route = route; traveled = targetDistance = 0;
        overlay.routes.clear(); List<RunRoute.Point> points = new ArrayList<>();
        for (int i = 0; i < route.size(); i++) points.add(route.get(i)); overlay.routes.add(points);
        overlay.reproject();
        setContentDescription(route.name + "，" + Math.round(route.lengthMeters) + " 米；真实地图预览"); updateMarker();
        if (style != null && !currentStyle().equals(loadedStyle)) loadStyle(); post(this::fit);
    }
    private String currentStyle() { return getContext().getSharedPreferences("route_editor", Context.MODE_PRIVATE).getString("style", RealMapStyle.DEFAULT_STYLE); }
    public void setDistance(double distance, boolean animate) {
        if (disposed || targetDistance == distance && (traveled == distance || movement != null)) return;
        cancelMovement(); targetDistance = distance;
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isLaidOut() || !isShown()) { traveled = distance; updateMarker(); return; }
        double from = traveled; movement = ValueAnimator.ofFloat(0, 1); movement.setDuration(220); movement.setInterpolator(new DecelerateInterpolator(2));
        movement.addUpdateListener(value -> { traveled = from + (distance - from) * (float) value.getAnimatedValue(); updateMarker(); }); movement.start();
    }
    private void updateMarker() {
        overlay.handles.clear();
        if (route != null) overlay.handles.add(new RouteHandleOverlay.Handle(route.at(traveled), "")); overlay.reprojectHandles();
    }
    private void cancelMovement() { if (movement != null) { movement.cancel(); movement = null; } }
    private void fit() {
        if (disposed || map == null || route == null || getWidth() == 0 || getHeight() == 0) return;
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (int i = 0; i < route.size(); i++) bounds.include(new LatLng(route.get(i).latitude, route.get(i).longitude));
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), Ui.dp(getContext(), 26))); overlay.reproject();
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { super.onSizeChanged(w, h, oldW, oldH); post(this::fit); }
    @Override public void onStart(@NonNull LifecycleOwner owner) { if (!disposed && !started) { mapView.onStart(); started = true; } }
    @Override public void onResume(@NonNull LifecycleOwner owner) {
        if (!disposed && !resumed) { mapView.onResume(); resumed = true; }
        if (style != null && !currentStyle().equals(loadedStyle)) loadStyle();
    }
    @Override public void onPause(@NonNull LifecycleOwner owner) { cancelMovement(); if (!disposed && resumed) { mapView.onPause(); resumed = false; } }
    @Override public void onStop(@NonNull LifecycleOwner owner) { if (!disposed && started) { mapView.onStop(); started = false; } }
    @Override public void onDestroy(@NonNull LifecycleOwner owner) { dispose(); }
    @Override protected void onDetachedFromWindow() { dispose(); super.onDetachedFromWindow(); }
    private void dispose() {
        if (disposed) return;
        cancelMovement(); if (style != null) style.dispose();
        if (resumed) mapView.onPause(); if (started) mapView.onStop(); mapView.onDestroy(); disposed = true;
        if (owner != null) owner.getLifecycle().removeObserver(this);
    }
    public void onLowMemory() { if (!disposed) mapView.onLowMemory(); }
}
