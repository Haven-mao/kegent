package com.haven.kegent.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A native Canvas rendition of the requested day/night capsule toggle. */
public final class DayNightToggleView extends View {
    private static final float DESIGN_WIDTH = 76f;
    private static final float DESIGN_HEIGHT = 42f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final RectF rect = new RectF();
    private boolean dark;
    private float progress;
    private ValueAnimator animator;

    public DayNightToggleView(Context context, boolean dark) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClickable(true);
        setMinimumWidth(Ui.dp(context, DESIGN_WIDTH));
        setMinimumHeight(Ui.dp(context, 48));
        this.dark = dark;
        progress = dark ? 1f : 0f;
        updateDescription();
    }

    public void setDarkMode(boolean dark, boolean animate) {
        this.dark = dark;
        updateDescription();
        float target = dark ? 1f : 0f;
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || !isLaidOut()) {
            cancelAnimation();
            progress = target;
            invalidate();
            return;
        }
        cancelAnimation();
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(650);
        animator.setInterpolator(new DecelerateInterpolator(1.7f));
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float maxWidth = DESIGN_WIDTH * density;
        float maxHeight = DESIGN_HEIGHT * density;
        float scale = Math.min(getWidth() / maxWidth, getHeight() / (48f * density));
        float left = (getWidth() - maxWidth * scale) * 0.5f;
        float top = (getHeight() - 48f * density * scale) * 0.5f;
        canvas.save();
        canvas.translate(left, top);
        canvas.scale(scale, scale);
        drawCapsule(canvas, density);
        canvas.restore();
    }

    private void drawCapsule(Canvas canvas, float unit) {
        float w = DESIGN_WIDTH * unit;
        float h = DESIGN_HEIGHT * unit;
        Path capsule = new Path();
        rect.set(0, 3f * unit, w, 3f * unit + h);
        capsule.addRoundRect(rect, 24f * unit, 24f * unit, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(capsule);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mix(Color.rgb(37, 143, 222), Color.rgb(20, 27, 50), progress));
        canvas.drawRoundRect(rect, 24f * unit, 24f * unit, paint);

        float centerX = (21f + 32f * progress) * unit;
        float centerY = 24f * unit;
        drawGlow(canvas, centerX, centerY, 46f * unit, 0.15f - 0.05f * progress);
        drawGlow(canvas, centerX, centerY, 37f * unit, 0.15f - 0.05f * progress);
        drawGlow(canvas, centerX, centerY, 27f * unit, 0.15f - 0.05f * progress);

        drawStars(canvas, unit);
        drawCloud(canvas, unit, true);
        drawCloud(canvas, unit, false);

        float sunX = centerX;
        canvas.save();
        canvas.rotate(14f * progress, sunX, centerY);
        float ballLeft = (6f + 32f * progress) * unit;
        float ballTop = 8f * unit;
        paint.setColor(mix(Color.rgb(255, 244, 38), Color.rgb(228, 230, 232), progress));
        canvas.drawCircle(sunX, centerY, 15f * unit, paint);
        if (progress > 0.01f) {
            paint.setColor(Color.argb((int) (175 * progress), 164, 166, 168));
            drawOval(canvas, ballLeft + 15f * unit, ballTop + 5f * unit, 9f * unit, 9f * unit);
            drawOval(canvas, ballLeft + 5f * unit, ballTop + 13f * unit, 12f * unit, 12f * unit);
            drawOval(canvas, ballLeft + 21f * unit, ballTop + 23f * unit, 4f * unit, 4f * unit);
        }
        canvas.restore();
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(unit);
        paint.setColor(progress < 0.5f ? Color.rgb(220, 228, 223) : Color.rgb(66, 80, 70));
        rect.set(0, 3f * unit, w, 3f * unit + h);
        canvas.drawRoundRect(rect, 24f * unit, 24f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGlow(Canvas canvas, float x, float y, float radius, float alpha) {
        paint.setColor(Color.argb((int) (255 * alpha), 225, 242, 255));
        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawStars(Canvas canvas, float unit) {
        float[][] stars = {
            {10, 11, 9, 10}, {25, 5, 12, 13}, {22, 29, 7, 8},
            {5, 24, 5, 5}, {34, 22, 4, 4}, {16, 4, 3, 3}
        };
        paint.setColor(Color.argb((int) (235 * progress), 245, 249, 255));
        for (float[] star : stars) {
            float x = (star[0] + star[2] * 0.5f) * unit;
            float y = (star[1] + star[3] * 0.5f - 23f * (1f - progress)) * unit;
            drawFourPointStar(canvas, x, y, Math.max(star[2], star[3]) * 0.5f * unit);
        }
    }

    private void drawFourPointStar(Canvas canvas, float cx, float cy, float radius) {
        Path star = new Path();
        star.moveTo(cx, cy - radius);
        star.lineTo(cx + radius * 0.28f, cy - radius * 0.28f);
        star.lineTo(cx + radius, cy);
        star.lineTo(cx + radius * 0.28f, cy + radius * 0.28f);
        star.lineTo(cx, cy + radius);
        star.lineTo(cx - radius * 0.28f, cy + radius * 0.28f);
        star.lineTo(cx - radius, cy);
        star.lineTo(cx - radius * 0.28f, cy - radius * 0.28f);
        star.close();
        canvas.drawPath(star, paint);
    }

    private void drawCloud(Canvas canvas, float unit, boolean back) {
        float cloudLeft = (back ? 9f : 12f) * unit;
        float cloudTop = (back ? 24f : 29f) * unit;
        float shift = progress * (back ? 38f : 43f) * unit;
        int alpha = (int) (255 * (1f - progress));
        int color = back ? Color.rgb(203, 217, 230) : Color.rgb(242, 244, 245);
        paint.setColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        rect.set(cloudLeft, cloudTop + shift, cloudLeft + 67f * unit, cloudTop + 24f * unit + shift);
        canvas.drawRoundRect(rect, 12f * unit, 12f * unit, paint);
        drawOval(canvas, cloudLeft + 44f * unit, cloudTop - 20f * unit + shift, 29f * unit, 29f * unit);
        drawOval(canvas, cloudLeft + 31f * unit, cloudTop - 6f * unit + shift, 26f * unit, 20f * unit);
        drawOval(canvas, cloudLeft + 16f * unit, cloudTop + 1f * unit + shift, 25f * unit, 18f * unit);
        drawOval(canvas, cloudLeft - 2f * unit, cloudTop + 9f * unit + shift, 25f * unit, 14f * unit);
    }

    private void drawOval(Canvas canvas, float left, float top, float width, float height) {
        rect.set(left, top, left + width, top + height);
        canvas.drawOval(rect, paint);
    }

    private void updateDescription() {
        setContentDescription(dark ? "切换到浅色模式" : "切换到深色模式");
    }

    private void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private static int mix(int start, int end, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                (int) (Color.red(start) + (Color.red(end) - Color.red(start)) * t),
                (int) (Color.green(start) + (Color.green(end) - Color.green(start)) * t),
                (int) (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * t));
    }
}
