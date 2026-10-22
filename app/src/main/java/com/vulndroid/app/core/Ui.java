package com.vulndroid.app.core;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.R;

public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    public static int color(Context context, int colorRes) {
        return context.getColor(colorRes);
    }

    public static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(dp(context, 4), 1f);
        return view;
    }

    public static TextView sectionLabel(Context context, String value) {
        TextView view = text(context, value, 11f, color(context, R.color.vd_text_faint));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        view.setLetterSpacing(0.14f);
        return view;
    }

    public static TextView body(Context context, String value) {
        TextView view = text(context, value, 14f, color(context, R.color.vd_text_muted));
        view.setLineSpacing(dp(context, 5), 1f);
        return view;
    }

    public static TextView strong(Context context, String value) {
        TextView view = text(context, value, 15f, color(context, R.color.vd_text));
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return view;
    }

    public static TextView mono(Context context, String value) {
        TextView view = text(context, value, 12f, color(context, R.color.vd_primary));
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        return view;
    }

    public static TextView codeBlock(Context context, String value) {
        TextView view = mono(context, value);
        view.setBackgroundResource(R.drawable.bg_code);
        int pad = dp(context, 14);
        view.setPadding(pad, pad, pad, pad);
        view.setTextSize(11.5f);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    public static LinearLayout card(Context context) {
        return card(context, R.drawable.bg_card);
    }

    public static LinearLayout card(Context context, int backgroundRes) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundResource(backgroundRes);
        int pad = dp(context, 18);
        layout.setPadding(pad, pad, pad, pad);
        return layout;
    }

    public static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, heightDp)));
        return view;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(color(context, R.color.vd_border_soft));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.topMargin = dp(context, 14);
        params.bottomMargin = dp(context, 14);
        view.setLayoutParams(params);
        return view;
    }

    public static TextView statusChip(Context context, LabTestResult.Status status) {
        TextView chip = new TextView(context);
        chip.setTextSize(10f);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        chip.setLetterSpacing(0.06f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        styleStatusChip(chip, status);
        return chip;
    }

    public static void styleStatusChip(TextView chip, LabTestResult.Status status) {
        Context context = chip.getContext();
        chip.setText("\u25CF  " + label(status));
        chip.setTextColor(statusColor(context, status));
        chip.setBackgroundResource(statusBackground(status));
    }

    public static String label(LabTestResult.Status status) {
        switch (status) {
            case VULNERABLE:
                return "VULNERABLE";
            case FIXED:
                return "FIXED";
            case INCONCLUSIVE:
                return "INCONCLUSIVE";
            case NOT_TESTED:
            default:
                return "NOT TESTED";
        }
    }

    public static int statusColor(Context context, LabTestResult.Status status) {
        switch (status) {
            case VULNERABLE:
                return color(context, R.color.vd_red);
            case FIXED:
                return color(context, R.color.vd_green);
            case INCONCLUSIVE:
                return color(context, R.color.vd_amber);
            case NOT_TESTED:
            default:
                return color(context, R.color.vd_text_faint);
        }
    }

    public static int statusBackground(LabTestResult.Status status) {
        switch (status) {
            case VULNERABLE:
                return R.drawable.bg_chip_red;
            case FIXED:
                return R.drawable.bg_chip_green;
            case INCONCLUSIVE:
                return R.drawable.bg_chip_amber;
            case NOT_TESTED:
            default:
                return R.drawable.bg_chip_gray;
        }
    }

    public static TextView badge(Context context, String text, int textColor, int backgroundRes) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(9.5f);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        view.setLetterSpacing(0.1f);
        view.setTextColor(textColor);
        view.setBackgroundResource(backgroundRes);
        view.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
        return view;
    }

    public static GradientDrawable rounded(int fillColor, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static void tint(ImageView view, int color) {
        view.setColorFilter(color);
    }

    public static void setMargins(View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(dp(view.getContext(), left), dp(view.getContext(), top),
                    dp(view.getContext(), right), dp(view.getContext(), bottom));
            view.setLayoutParams(params);
        }
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(Context context, int widthDp, int heightDp) {
        return new LinearLayout.LayoutParams(
                widthDp == ViewGroup.LayoutParams.MATCH_PARENT
                        ? ViewGroup.LayoutParams.MATCH_PARENT : dp(context, widthDp),
                heightDp == ViewGroup.LayoutParams.WRAP_CONTENT
                        ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(context, heightDp));
    }

    public static void add(LinearLayout parent, View child, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(parent.getContext(), topMarginDp);
        parent.addView(child, params);
    }
}
