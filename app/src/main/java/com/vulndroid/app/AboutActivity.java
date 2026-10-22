package com.vulndroid.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.core.Ui;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        findViewById(R.id.aboutBack).setOnClickListener(v -> finish());
        LinearLayout container = findViewById(R.id.aboutContainer);

        addCard(container, "WHY THIS EXISTS",
                "VulnDroid is a local Android security lab. Each lab contains a deliberately "
                        + "vulnerable implementation, a test that produces real evidence, and a "
                        + "hardened counterpart. The goal is to connect configuration and code to "
                        + "observable impact — and then verify a fix.");

        addCard(container, "HOW STATUS WORKS",
                "A lab starts at NOT TESTED. Pressing the retest button runs an actual check in this "
                        + "APK: a filesystem scan, an APK string scan, a PackageManager inspection, a "
                        + "URI-handler decision, or a real TLS handshake against a self-signed loopback "
                        + "endpoint. Only that result changes the status.\n\n"
                        + "VULNERABLE means the test observed the weakness. FIXED means it observed the "
                        + "control working. INCONCLUSIVE means the test could not establish either. "
                        + "Results are stored locally per installation and are included in the report.");

        addCard(container, "TWO BUILDS",
                "This project builds two APKs from one codebase:\n\n"
                        + "• com.vulndroid.app — vulnerable training build\n"
                        + "• com.vulndroid.app.secure — hardened reference build\n\n"
                        + "Install both side by side. Vulnerability-specific code lives in the flavor "
                        + "source sets, so the secure APK does not contain the trust-all TLS client or "
                        + "the embedded demo key. The attacker app is a third APK.");

        addCard(container, "COMPANION ATTACKER",
                "VulnDroid Attacker (com.vulndroid.attacker) launches the exported profile component "
                        + "and competes for the vulndroid:// deep-link scheme. It is a test tool, not a "
                        + "general-purpose exploit. Use only against the VulnDroid packages on a test "
                        + "device.");

        addCard(container, "SAFETY AND CLEANUP",
                "Everything here is fake: accounts, passwords, tokens, the API key, and the local TLS "
                        + "certificate. Do not deploy these builds to production or use them with real "
                        + "data.\n\n"
                        + "Remove the apps and their data with:\n\n"
                        + "adb uninstall com.vulndroid.app\n"
                        + "adb uninstall com.vulndroid.app.secure\n"
                        + "adb uninstall com.vulndroid.attacker\n\n"
                        + "The debug builds are intentionally debuggable so adb run-as can inspect the "
                        + "sandbox for the storage lab.");

        addCard(container, "BUILD AND TEST",
                "From the project root:\n\n"
                        + "./gradlew :app:assembleVulnDebug :app:assembleSecureDebug :attacker:assembleDebug\n\n"
                        + "APKs:\n"
                        + "app/build/outputs/apk/vuln/debug/app-vuln-debug.apk\n"
                        + "app/build/outputs/apk/secure/debug/app-secure-debug.apk\n"
                        + "attacker/build/outputs/apk/debug/attacker-debug.apk\n\n"
                        + "The repository includes a lab manual, architecture notes, a remediation "
                        + "guide, and an optional desktop HTTPS server under tools/.");

        addCard(container, "LIMITS",
                "This is a teaching environment, not a scanner. The tests cover the specific flaw "
                        + "demonstrated in each lab and do not prove an app is secure. Real assessments "
                        + "need threat modelling, code review, dynamic instrumentation, API testing, "
                        + "and authorization testing beyond the client.");

        TextView footer = Ui.mono(this, "VulnDroid v" + BuildConfig.VERSION_NAME
                + "  ·  " + getPackageName() + "\n"
                + (BuildConfig.VULNERABLE_BUILD ? "Vulnerable training build" : "Secure reference build")
                + "  ·  Java + XML  ·  minSdk 24");
        footer.setTextSize(10.5f);
        footer.setTextColor(Ui.color(this, R.color.vd_text_faint));
        footer.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 24));
        container.addView(footer);
    }

    private void addCard(LinearLayout container, String title, String body) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.sectionLabel(this, title));
        TextView text = Ui.body(this, body);
        text.setPadding(0, Ui.dp(this, 9), 0, 0);
        card.addView(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 14);
        container.addView(card, params);
    }
}
