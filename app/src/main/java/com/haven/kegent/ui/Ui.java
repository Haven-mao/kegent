package com.haven.kegent.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.haven.kegent.R;

public final class Ui {
    private Ui() {}

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static int color(Context c, int id) {
        return androidx.core.content.ContextCompat.getColor(c, id);
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static TextView text(Context c, String value, int size, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color(c, R.color.ui_text));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(c, 3), 1);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView note(Context c, String value) {
        TextView t = text(c, value, 13, false);
        t.setTextColor(color(c, R.color.ui_muted));
        return t;
    }

    public static MaterialButton button(Context c, String text, boolean primary) {
        MaterialButton b =
                new MaterialButton(
                        c,
                        null,
                        primary
                                ? com.google.android.material.R.attr.materialButtonStyle
                                : com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setCornerRadius(dp(c, 14));
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setMinimumWidth(0);
        b.setMinWidth(0);
        b.setInsetTop(dp(c, 3));
        b.setInsetBottom(dp(c, 3));
        b.setTextColor(
                controlColors(
                        c,
                        R.color.ui_disabled_text,
                        primary ? R.color.on_primary_brand : R.color.ui_accent));
        b.setBackgroundTintList(
                controlColors(
                        c,
                        R.color.ui_disabled_fill,
                        primary ? R.color.primary_brand : R.color.ui_tonal));
        b.setStrokeWidth(dp(c, primary ? 2 : 1.5f));
        b.setStrokeColor(
                controlColors(
                        c,
                        R.color.ui_outline,
                        primary ? R.color.ui_accent : R.color.ui_control_outline));
        return b;
    }

    private static ColorStateList controlColors(Context c, int disabled, int enabled) {
        return new ColorStateList(
                new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
                new int[] {color(c, disabled), color(c, enabled)});
    }

    public static MaterialCardView card(Context c, View content) {
        MaterialCardView card = new MaterialCardView(c);
        card.setRadius(dp(c, 20));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(c, 1));
        card.setStrokeColor(color(c, R.color.ui_outline));
        card.setCardBackgroundColor(color(c, R.color.ui_surface));
        card.addView(content);
        return card;
    }

    public static TextInputLayout field(Context c, String label, String value, int inputType) {
        TextInputLayout box =
                new TextInputLayout(c, null, com.google.android.material.R.attr.textInputStyle);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setHint(label);
        box.setBoxCornerRadii(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        box.setDefaultHintTextColor(ColorStateList.valueOf(color(c, R.color.ui_muted)));
        box.setHintTextColor(ColorStateList.valueOf(color(c, R.color.ui_accent)));
        box.setBoxStrokeColor(color(c, R.color.ui_accent));
        TextInputEditText edit = new TextInputEditText(box.getContext());
        edit.setText(value);
        edit.setSingleLine();
        edit.setTextSize(16);
        edit.setInputType(inputType);
        edit.setTextColor(color(c, R.color.ui_text));
        edit.setMinHeight(dp(c, 56));
        edit.setSelectAllOnFocus(true);
        box.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }

    public static String value(TextInputLayout field) {
        return field.getEditText().getText().toString().trim();
    }

    public static void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(parent.getContext(), top);
        parent.addView(child, p);
    }

    public static void pair(LinearLayout parent, View a, View b, int top) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        p.setMarginEnd(dp(parent.getContext(), 6));
        row.addView(a, p);
        LinearLayout.LayoutParams q = new LinearLayout.LayoutParams(0, -2, 1);
        q.setMarginStart(dp(parent.getContext(), 6));
        row.addView(b, q);
        add(parent, row, top);
    }

    public static void reveal(View v) {
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        if (ValueAnimator.areAnimatorsEnabled()) {
            v.setAlpha(0.6f);
            v.setTranslationY(dp(v.getContext(), 6));
            v.animate()
                    .alpha(1)
                    .translationY(0)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2))
                    .start();
        } else {
            v.setAlpha(1);
            v.setTranslationY(0);
        }
    }
}
