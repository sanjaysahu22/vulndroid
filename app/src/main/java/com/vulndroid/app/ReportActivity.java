package com.vulndroid.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.core.LabCatalog;
import com.vulndroid.app.core.LabInfo;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabStatusStore;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportActivity extends Activity {

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        container = findViewById(R.id.reportContainer);
        findViewById(R.id.reportBack).setOnClickListener(v -> finish());
        findViewById(R.id.reportShare).setOnClickListener(v -> share());
        findViewById(R.id.reportClear).setOnClickListener(v -> confirmClear());
        render();
    }

    private void render() {
        container.removeAllViews();
        LabInfo[] labs = LabCatalog.all();
        int total = labs.length;
        int tested = 0;
        int vulnerable = 0;
        int fixed = 0;
        int inconclusive = 0;
        for (LabInfo lab : labs) {
            LabTestResult result = LabStatusStore.get(this, lab.id);
            if (result.wasTested()) {
                tested++;
            }
            switch (result.status) {
                case VULNERABLE:
                    vulnerable++;
                    break;
                case FIXED:
                    fixed++;
                    break;
                case INCONCLUSIVE:
                    inconclusive++;
                    break;
                default:
                    break;
            }
        }

        LinearLayout summary = Ui.card(this);
        TextView kicker = Ui.sectionLabel(this, "INSTALLATION SUMMARY");
        summary.addView(kicker);
        TextView headline = Ui.text(this, tested + " of " + total + " labs tested", 22f,
                Ui.color(this, R.color.vd_text));
        headline.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        summary.addView(headline, marginTop(4));
        TextView counts = Ui.body(this, "Vulnerable: " + vulnerable + "    Fixed: " + fixed
                + "    Inconclusive: " + inconclusive + "    Untested: " + (total - tested));
        summary.addView(counts, marginTop(4));
        TextView meta = Ui.mono(this,
                "build=" + (BuildConfig.VULNERABLE_BUILD ? "vulnerable" : "secure")
                        + "\npackage=" + getPackageName()
                        + "\nandroid=" + android.os.Build.VERSION.RELEASE
                        + "\ngenerated=" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(new Date()));
        meta.setTextSize(10.5f);
        meta.setTextColor(Ui.color(this, R.color.vd_text_faint));
        summary.addView(meta, marginTop(12));
        addCard(summary);

        for (LabInfo lab : LabCatalog.all()) {
            LabTestResult result = LabStatusStore.get(this, lab.id);
            LabModule module = LabCatalog.module(this, lab.id);

            LinearLayout card = Ui.card(this);
            LinearLayout header = Ui.row(this);
            TextView title = Ui.text(this, String.format(Locale.US, "%02d  %s", lab.number, lab.title),
                    15f, Ui.color(this, R.color.vd_text));
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            header.addView(title, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            header.addView(Ui.statusChip(this, result.status));
            card.addView(header);

            TextView headlineView = Ui.strong(this, result.wasTested()
                    ? result.headline : "Not tested in this installation.");
            headlineView.setTextSize(13f);
            headlineView.setTextColor(Ui.color(this, R.color.vd_text_muted));
            card.addView(headlineView, marginTop(8));

            if (!result.evidence.isEmpty()) {
                TextView evidence = Ui.codeBlock(this, result.evidence);
                evidence.setTextSize(10f);
                card.addView(evidence, marginTop(10));
            }

            TextView remediationLabel = Ui.sectionLabel(this, "REMEDIATION");
            card.addView(remediationLabel, marginTop(14));
            TextView remediation = Ui.body(this, module.secureImplementation());
            remediation.setTextSize(12.5f);
            card.addView(remediation, marginTop(6));

            addCard(card);
        }

        TextView disclaimer = Ui.body(this,
                "This report is generated from actual in-app test results for this installation. "
                        + "It is a training artifact, not a production security assessment. Test "
                        + "results do not transfer between the vulnerable and secure APKs.");
        disclaimer.setTextSize(11.5f);
        disclaimer.setPadding(0, 0, 0, Ui.dp(this, 16));
        container.addView(disclaimer);
    }

    private void share() {
        String report = buildReportText();
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "VulnDroid security report (" + getPackageName() + ")");
        send.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(send, "Share VulnDroid report"));
    }

    private String buildReportText() {
        StringBuilder out = new StringBuilder();
        out.append("VULNDROID — MOBILE APPLICATION SECURITY LAB\n")
                .append("Security report for this installation\n")
                .append("========================================\n")
                .append("Build: ").append(BuildConfig.VULNERABLE_BUILD
                        ? "VULNERABLE (training)" : "SECURE (hardened reference)").append("\n")
                .append("Package: ").append(getPackageName()).append("\n")
                .append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n")
                .append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm Z", Locale.US)
                        .format(new Date())).append("\n\n");

        int tested = 0;
        LabInfo[] labs = LabCatalog.all();
        for (LabInfo lab : labs) {
            LabTestResult result = LabStatusStore.get(this, lab.id);
            if (result.wasTested()) {
                tested++;
            }
        }
        out.append("Tests executed: ").append(tested).append("/").append(labs.length).append("\n\n");

        for (LabInfo lab : labs) {
            LabTestResult result = LabStatusStore.get(this, lab.id);
            LabModule module = LabCatalog.module(this, lab.id);
            out.append("----------------------------------------\n")
                    .append("LAB ").append(lab.number).append(" — ").append(lab.title).append("\n")
                    .append("Status: ").append(result.statusLabel());
            if (result.wasTested()) {
                out.append("  (").append(result.formattedTime()).append(")");
            }
            out.append("\n");
            out.append("Result: ").append(result.wasTested()
                    ? result.headline : "No test executed.").append("\n");
            if (!result.evidence.isEmpty()) {
                out.append("Evidence:\n");
                for (String line : result.evidence.split("\n")) {
                    out.append("  ").append(line).append("\n");
                }
            }
            out.append("Remediation:\n");
            for (String line : module.secureImplementation().split("\n")) {
                out.append("  ").append(line).append("\n");
            }
            out.append("\n");
        }
        out.append("========================================\n")
                .append("Training use only. All accounts, tokens and secrets are fake.\n")
                .append("A green result covers only the specific test that ran in this build.\n");
        return out.toString();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear test results?")
                .setMessage("All lab statuses in this installation will return to NOT TESTED. "
                        + "The demos and stored demo data are left alone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    LabStatusStore.clearAll(this);
                    render();
                })
                .show();
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 14);
        container.addView(card, params);
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, dp);
        return params;
    }
}
