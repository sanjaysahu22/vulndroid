package com.vulndroid.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.core.LabCatalog;
import com.vulndroid.app.core.LabInfo;
import com.vulndroid.app.core.LabStatusStore;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout labList;
    private TextView progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        labList = findViewById(R.id.mainLabList);
        progress = findViewById(R.id.mainProgress);

        TextView badge = findViewById(R.id.mainBuildBadge);
        badge.setText(BuildConfig.VULNERABLE_BUILD ? "VULNERABLE BUILD" : "SECURE BUILD");
        badge.setTextColor(Ui.color(this, BuildConfig.VULNERABLE_BUILD
                ? R.color.vd_red : R.color.vd_green));
        badge.setBackgroundResource(BuildConfig.VULNERABLE_BUILD
                ? R.drawable.bg_chip_red : R.drawable.bg_chip_green);

        findViewById(R.id.mainReport).setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));
        findViewById(R.id.mainAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        TextView version = findViewById(R.id.mainVersion);
        version.setText(getPackageName() + "  ·  v" + BuildConfig.VERSION_NAME + "\n"
                + (BuildConfig.VULNERABLE_BUILD
                        ? "Vulnerable training build · install the secure build alongside it"
                        : "Hardened reference build · compare behavior with the vulnerable build")
                + "\nNo real credentials or user data are present.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderLabs();
    }

    private void renderLabs() {
        labList.removeAllViews();
        LabInfo[] labs = LabCatalog.all();
        int total = labs.length;
        int tested = 0;
        int vulnerable = 0;
        int fixed = 0;

        for (LabInfo lab : labs) {
            LabTestResult result = LabStatusStore.get(this, lab.id);
            if (result.wasTested()) {
                tested++;
            }
            if (result.status == LabTestResult.Status.VULNERABLE) {
                vulnerable++;
            }
            if (result.status == LabTestResult.Status.FIXED) {
                fixed++;
            }
            labList.addView(buildLabCard(lab, result));
        }

        if (tested == 0) {
            progress.setText("0 / " + total + " TESTED");
        } else {
            progress.setText(String.format(Locale.US, "%d / %d TESTED · %d VULN · %d FIXED",
                    tested, total, vulnerable, fixed));
        }
    }

    private View buildLabCard(LabInfo lab, LabTestResult result) {
        LinearLayout card = Ui.card(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout iconFrame = new LinearLayout(this);
        iconFrame.setGravity(Gravity.CENTER);
        iconFrame.setBackgroundResource(R.drawable.bg_icon_tile);
        ImageView icon = new ImageView(this);
        icon.setImageResource(lab.iconRes);
        icon.setColorFilter(Ui.color(this, R.color.vd_primary));
        iconFrame.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        card.addView(iconFrame, new LinearLayout.LayoutParams(Ui.dp(this, 46), Ui.dp(this, 46)));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.leftMargin = Ui.dp(this, 14);
        card.addView(column, columnParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView number = new TextView(this);
        number.setText(String.format(Locale.US, "%02d", lab.number));
        number.setTextSize(10f);
        number.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        number.setTextColor(Ui.color(this, R.color.vd_text_faint));
        titleRow.addView(number);
        TextView title = new TextView(this);
        title.setText(lab.title);
        title.setTextSize(15f);
        title.setTextColor(Ui.color(this, R.color.vd_text));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = Ui.dp(this, 8);
        titleRow.addView(title, titleParams);
        column.addView(titleRow);

        TextView tagline = new TextView(this);
        tagline.setText(lab.tagline);
        tagline.setTextSize(11.5f);
        tagline.setTextColor(Ui.color(this, R.color.vd_text_muted));
        tagline.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 7));
        column.addView(tagline);

        TextView status = Ui.statusChip(this, result.status);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        column.addView(status, statusParams);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setColorFilter(Ui.color(this, R.color.vd_text_faint));
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                Ui.dp(this, 18), Ui.dp(this, 18));
        chevronParams.leftMargin = Ui.dp(this, 8);
        card.addView(chevron, chevronParams);

        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, LabActivity.class);
            intent.putExtra("lab_id", lab.id);
            startActivity(intent);
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 10);
        card.setLayoutParams(params);
        return card;
    }
}
