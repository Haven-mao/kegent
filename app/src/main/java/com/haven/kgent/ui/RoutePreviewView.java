package com.haven.kgent.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.haven.kgent.R;
import com.haven.kgent.core.RunRoute;

/** A preview of actual route geometry, not map tiles or invented roads. */
public final class RoutePreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RunRoute route;
    private ValueAnimator movement;
    private double targetDistance;
    private double traveled, minX, minY, scale, offsetX, offsetY, cos;

    public RoutePreviewView(Context c) {
        super(c);
        setMinimumHeight(Ui.dp(c, 190));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setRoute(RunRoute route) {
        cancelMovement();
        this.route = route;
        targetDistance = 0;
        traveled = 0;
        setContentDescription(
                route == null
                        ? "未选择路线"
                        : route.name
                                + "，"
                                + route.size()
                                + " 个点，约 "
                                + Math.round(route.lengthMeters)
                                + " 米");
        invalidate();
    }

    public void setDistance(double distance, boolean animate) {
        if (targetDistance == distance && (traveled == distance || movement != null)) return;
        cancelMovement();
        targetDistance = distance;
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isLaidOut() || !isShown()) {
            traveled = distance;
            invalidate();
            return;
        }
        double from = traveled;
        movement = ValueAnimator.ofFloat(0, 1);
        movement.setDuration(220);
        movement.setInterpolator(new DecelerateInterpolator(2));
        movement.addUpdateListener(
                animation -> {
                    traveled = from + (distance - from) * (float) animation.getAnimatedValue();
                    invalidate();
                });
        movement.start();
    }

    private void cancelMovement() {
        if (movement != null) {
            movement.cancel();
            movement = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelMovement();
        traveled = targetDistance;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float pad = Math.min(Ui.dp(getContext(), 28), Math.min(getWidth(), getHeight()) * 0.16f);
        paint.setColor(Ui.color(getContext(), R.color.ui_tonal));
        paint.setStyle(Paint.Style.FILL);
        c.drawRoundRect(
                0,
                0,
                getWidth(),
                getHeight(),
                Ui.dp(getContext(), 14),
                Ui.dp(getContext(), 14),
                paint);
        if (route == null) return;
        cos = Math.max(0.01, Math.cos(Math.toRadians(route.get(0).latitude)));
        minX = Double.MAX_VALUE;
        minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            RunRoute.Point p = route.get(i);
            double x = p.longitude * cos, y = -p.latitude;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        scale =
                Math.min(
                        (getWidth() - 2 * pad) / Math.max(maxX - minX, 0.000001),
                        (getHeight() - 2 * pad) / Math.max(maxY - minY, 0.000001));
        offsetX = (getWidth() - (maxX - minX) * scale) / 2;
        offsetY = (getHeight() - (maxY - minY) * scale) / 2;
        Path all = new Path();
        for (int i = 0; i < route.size(); i++) {
            RunRoute.Point p = route.get(i);
            if (i == 0) all.moveTo(x(p), y(p));
            else all.lineTo(x(p), y(p));
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Ui.color(getContext(), R.color.ui_outline));
        c.drawPath(all, paint);
        double position = route.routePosition(traveled);
        Path covered = new Path();
        covered.moveTo(x(route.get(0)), y(route.get(0)));
        for (int i = 1; i < route.size() && route.distanceAt(i) <= position; i++)
            covered.lineTo(x(route.get(i)), y(route.get(i)));
        RunRoute.Point point = route.at(traveled);
        covered.lineTo(x(point), y(point));
        paint.setColor(Ui.color(getContext(), R.color.ui_accent));
        c.drawPath(traveled > 0 ? covered : all, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.color(getContext(), R.color.ui_surface));
        c.drawCircle(x(point), y(point), Ui.dp(getContext(), 7), paint);
        paint.setColor(Ui.color(getContext(), R.color.ui_accent));
        c.drawCircle(x(point), y(point), Ui.dp(getContext(), 4), paint);
    }

    private float x(RunRoute.Point p) {
        return (float) (offsetX + (p.longitude * cos - minX) * scale);
    }

    private float y(RunRoute.Point p) {
        return (float) (offsetY + (-p.latitude - minY) * scale);
    }
}
