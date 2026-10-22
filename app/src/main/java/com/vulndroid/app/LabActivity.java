package com.vulndroid.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.vulndroid.app.core.LabCatalog;
import com.vulndroid.app.core.LabInfo;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabStatusStore;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

import java.util.Locale;

public class LabActivity extends Activity {

    private LabInfo info;
    private LabModule module;
    private TextView statusChip;
    private TextView observedHeadline;
    private TextView observedTime;
    private TextView evidenceLabel;
    private TextView evidence;
    private TextView retest;
    private ProgressBar progress;
    private boolean running;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lab);

        String labId = getIntent().getStringExtra("lab_id");
        if (labId == null) {
            labId = LabCatalog.STORAGE;
        }
        info = LabCatalog.info(labId);
        module = LabCatalog.module(this, labId);

        findViewById(R.id.labBack).setOnClickListener(v -> finish());
        statusChip = findViewById(R.id.labStatus);
        observedHeadline = findViewById(R.id.labObservedHeadline);
        observedTime = findViewById(R.id.labObservedTime);
        evidenceLabel = findViewById(R.id.labEvidenceLabel);
        evidence = findViewById(R.id.labEvidence);
        retest = findViewById(R.id.labRetest);
        progress = findViewById(R.id.labProgress);

        ((TextView) findViewById(R.id.labKicker)).setText(
                String.format(Locale.US, "LAB %02d  ·  %s", info.number,
                        info.shortTitle.toUpperCase(Locale.US)));
        ((TextView) findViewById(R.id.labTitle)).setText(info.title);
        ((TextView) findViewById(R.id.labIntro)).setText(module.intro());
        ((TextView) findViewById(R.id.labVulnerability)).setText(module.vulnerability());
        ((TextView) findViewById(R.id.labTesting)).setText(module.testing());
        ((TextView) findViewById(R.id.labSecure)).setText(module.secureImplementation());
        ((TextView) findViewById(R.id.labSummary)).setText(module.summary());

        LinearLayout demoCard = findViewById(R.id.labDemoCard);
        View demo = getLayoutInflater().inflate(module.demoLayoutRes(), demoCard, false);
        demoCard.addView(demo);
        module.bindDemo(demo, this);

        retest.setText(BuildConfig.VULNERABLE_BUILD
                ? "RUN VULNERABILITY TEST" : "RUN SECURE RETEST");
        ((TextView) findViewById(R.id.labBuildFooter)).setText(
                getPackageName() + "  ·  v" + BuildConfig.VERSION_NAME + "  ·  "
                        + (BuildConfig.VULNERABLE_BUILD ? "VULNERABLE" : "SECURE") + " build\n"
                        + "Status is recorded only from tests executed in this installation.");

        renderResult(LabStatusStore.get(this, info.id));
        retest.setOnClickListener(v -> runTest());
    }

    private void runTest() {
        if (running) {
            return;
        }
        running = true;
        retest.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        statusChip.setVisibility(View.INVISIBLE);
        observedHeadline.setText("Running active test…");
        observedHeadline.setTextColor(Ui.color(this, R.color.vd_text_muted));
        observedTime.setText("");
        evidence.setVisibility(View.GONE);
        evidenceLabel.setVisibility(View.GONE);

        new Thread(() -> {
            LabTestResult result;
            try {
                result = module.runTest(this);
            } catch (Exception e) {
                result = LabTestResult.inconclusive(
                        "The test threw an exception before producing a result.",
                        e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            LabStatusStore.save(this, info.id, result);
            final LabTestResult finalResult = result;
            runOnUiThread(() -> {
                running = false;
                progress.setVisibility(View.GONE);
                retest.setEnabled(true);
                statusChip.setVisibility(View.VISIBLE);
                renderResult(finalResult);
            });
        }, "vulndroid-lab-test").start();
    }

    private void renderResult(LabTestResult result) {
        Ui.styleStatusChip(statusChip, result.status);
        statusChip.setVisibility(View.VISIBLE);

        if (!result.wasTested()) {
            observedHeadline.setText(R.string.observed_empty);
            observedHeadline.setTextColor(Ui.color(this, R.color.vd_text_muted));
            observedTime.setText("");
            evidence.setVisibility(View.GONE);
            evidenceLabel.setVisibility(View.GONE);
            return;
        }

        observedHeadline.setText(result.headline);
        observedHeadline.setTextColor(Ui.color(this, R.color.vd_text));
        observedTime.setText(result.statusLabel() + "  ·  " + result.formattedTime());
        if (!result.evidence.isEmpty()) {
            evidenceLabel.setVisibility(View.VISIBLE);
            evidence.setVisibility(View.VISIBLE);
            evidence.setText(result.evidence);
            evidence.setTextColor(Ui.statusColor(this, result.status));
            evidence.setBackgroundResource(R.drawable.bg_code);
        } else {
            evidenceLabel.setVisibility(View.GONE);
            evidence.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!running && statusChip != null) {
            renderResult(LabStatusStore.get(this, info.id));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (module != null) {
            module.onDestroy();
        }
    }
}
