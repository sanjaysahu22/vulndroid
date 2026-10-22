package com.vulndroid.app.labs.secrets;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.vulndroid.app.R;
import com.vulndroid.app.core.ApkScanner;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.SecretNeedle;
import com.vulndroid.app.core.Ui;

import java.io.File;
import java.util.Locale;

public final class SecretsLabModule implements LabModule {

    @Override
    public String intro() {
        return "An APK is a readable archive. Code, resources, and string constants ship to every "
                + "user, and reverse-engineering tools make recovery routine. A secret in client "
                + "code is not hidden by compilation, minification, or a private-looking variable name.\n\n"
                + "This lab embeds a fake API key in the vulnerable build and then recovers it from "
                + "the installed APK, exactly as JADX or MobSF would.";
    }

    @Override
    public String vulnerability() {
        return "The vulnerable build contains a private-looking API key as a static field. DEX string "
                + "constants are easy to locate with JADX, `strings`, or MobSF. Anyone who downloads "
                + "the APK can reuse that key against the service, consume quota, reach data the key "
                + "authorises, or keep access until the key is rotated.\n\n"
                + "If the key is a real production credential, the impact can extend far beyond the "
                + "app. Obfuscation raises the effort slightly; it does not make the value secret.";
    }

    @Override
    public String testing() {
        return "1. In this screen, tap EXTRACT SECRET FROM THIS APK. The in-app scanner walks the "
                + "same ZIP entries a reverse engineer would inspect.\n"
                + "2. Confirm with JADX:\n\n"
                + "jadx -d jadx-vuln app/build/outputs/apk/vuln/debug/app-vuln-debug.apk\n"
                + "grep -R \"DEMO_KEY\" jadx-vuln\n\n"
                + "3. Or skip the decompiler. The APK is split across several DEX files, so scan "
                + "all of them:\n\n"
                + "unzip -p app/build/outputs/apk/vuln/debug/app-vuln-debug.apk '*.dex' | strings | grep DEMO_KEY\n\n"
                + "4. Load the APK in MobSF and check the strings / hardcoded-secrets findings.\n"
                + "5. Run the same commands on app-secure-debug.apk. The full demo key is not present "
                + "in the secure build; only a public client identifier remains.\n\n"
                + "A match in one of the DEX files is the evidence. Record the entry name and offset.";
    }

    @Override
    public String secureImplementation() {
        return "Move the private credential to a trusted backend and keep only a public client "
                + "identifier in the app. The client authenticates to your API, the backend checks "
                + "the caller and authorization, and it returns a short-lived, narrowly scoped token "
                + "or performs the privileged call itself.\n\n"
                + "Operational requirements: rotate any key that was exposed, scope keys per service, "
                + "monitor for abuse, and never treat obfuscation or a mobile attestation signal as a "
                + "secret store. Public identifiers such as OAuth client IDs are fine in an APK when "
                + "the server enforces redirect URIs, scopes, and caller authorization.\n\n"
                + "The secure flavor contains no private key. Its self-scan therefore finds no match.";
    }

    @Override
    public String summary() {
        return "Assume every byte of the APK is public. A secret is only secret when it stays on "
                + "a system the user cannot read: usually your backend.\n\n"
                + "The vulnerable build proves extraction. The secure build proves absence — but "
                + "absence in the client only works if the server side enforces authorization and "
                + "rotates credentials that were ever shipped.";
    }

    @Override
    public int demoLayoutRes() {
        return R.layout.demo_secrets;
    }

    @Override
    public void bindDemo(View demo, Activity activity) {
        TextView maskedKey = demo.findViewById(R.id.secretsMaskedKey);
        TextView modeNote = demo.findViewById(R.id.secretsModeNote);
        TextView output = demo.findViewById(R.id.secretsOutput);
        Button extract = demo.findViewById(R.id.secretsExtract);
        Button call = demo.findViewById(R.id.secretsCall);

        String key = SecretSource.apiKey();
        if (key != null) {
            maskedKey.setText(key);
            maskedKey.setTextColor(Ui.color(activity, R.color.vd_red));
            modeNote.setText(SecretSource.modeDescription());
        } else {
            maskedKey.setText("No private key in this APK");
            maskedKey.setTextColor(Ui.color(activity, R.color.vd_green));
            modeNote.setText(SecretSource.modeDescription() + "  Public client ID: "
                    + SecretSource.publicClientId());
        }

        extract.setOnClickListener(v -> {
            extract.setEnabled(false);
            output.setVisibility(View.VISIBLE);
            output.setText("Scanning APK…");
            new Thread(() -> {
                String report = extractionReport(activity);
                activity.runOnUiThread(() -> {
                    extract.setEnabled(true);
                    output.setText(report);
                    output.setTextColor(Ui.color(activity, SecretSource.apiKey() != null
                            ? R.color.vd_red : R.color.vd_green));
                });
            }).start();
        });

        call.setOnClickListener(v -> {
            output.setVisibility(View.VISIBLE);
            if (SecretSource.apiKey() != null) {
                output.setTextColor(Ui.color(activity, R.color.vd_red));
                output.setText("POST /v1/accounts\n"
                        + "Authorization: ApiKey " + SecretSource.apiKey() + "\n\n"
                        + "200 OK\n{\"account\":\"alice\",\"balance\":\"$1,250.00\"}\n\n"
                        + "The request is authorised by the key that just shipped to every user.");
            } else {
                output.setTextColor(Ui.color(activity, R.color.vd_green));
                output.setText("POST /v1/token\n"
                        + "client_id=" + SecretSource.publicClientId() + "\n\n"
                        + "200 OK\n{\"access_token\":\"short-lived-scoped-token\",\"expires_in\":300}\n\n"
                        + "The backend holds the private key, checks authorization, and issues a "
                        + "narrow, expiring token.");
            }
        });
    }

    private String extractionReport(Activity activity) {
        File apk = new File(activity.getApplicationInfo().sourceDir);
        try {
            String key = SecretSource.apiKey();
            if (key != null) {
                ApkScanner.Hit hit = ApkScanner.findString(apk, key);
                if (hit == null) {
                    return "Scanned " + apk.getName() + "\nNo match found — the scanner may be looking "
                            + "at an unexpected split APK.";
                }
                return "APK: " + apk.getAbsolutePath() + "\n"
                        + "ENTRY: " + hit.entryName + "\n"
                        + "OFFSET: 0x" + Long.toHexString(hit.offset).toUpperCase(Locale.US) + "\n"
                        + "OCCURRENCES: " + hit.occurrences + "\n\n"
                        + "VALUE: " + key + "\n\n"
                        + "This is a fake training key. In a real APK the same steps recover a live "
                        + "credential.";
            }

            ApkScanner.Hit hit = ApkScanner.findString(apk, SecretNeedle.demoApiKey());
            if (hit != null) {
                return "Unexpected: the demo key was still found in " + hit.entryName
                        + " at offset 0x" + Long.toHexString(hit.offset) + ".";
            }
            return "APK: " + apk.getAbsolutePath() + "\n"
                    + "Scan result: no embedded demo API key.\n\n"
                    + "SecretSource.apiKey() = null\n"
                    + "Public client ID = " + SecretSource.publicClientId() + "\n\n"
                    + "The private credential lives on a backend and is obtained as a short-lived, "
                    + "scoped token.";
        } catch (Exception e) {
            return "APK scan failed: " + e.getClass().getSimpleName() + " — " + e.getMessage();
        }
    }

    @Override
    public LabTestResult runTest(Activity activity) {
        File apk = new File(activity.getApplicationInfo().sourceDir);
        String key = SecretSource.apiKey();
        try {
            if (key != null) {
                ApkScanner.Hit hit = ApkScanner.findString(apk, key);
                if (hit == null) {
                    return LabTestResult.inconclusive(
                            "The build contains a secret source, but the APK scan found no match.",
                            "APK: " + apk.getAbsolutePath());
                }
                return LabTestResult.vulnerable(
                        "API key recovered from the APK without executing the app.",
                        "APK: " + apk.getAbsolutePath() + "\n"
                                + "Recovered value: " + key + "\n"
                                + "Entry: " + hit.entryName + "\n"
                                + "Offset: 0x" + Long.toHexString(hit.offset).toUpperCase(Locale.US)
                                + "\nOccurrences: " + hit.occurrences
                                + "\n\nEquivalent external check:\n"
                                + "unzip -p <apk> '*.dex' | strings | grep DEMO_KEY");
            }

            ApkScanner.Hit hit = ApkScanner.findString(apk, SecretNeedle.demoApiKey());
            if (hit != null) {
                return LabTestResult.vulnerable(
                        "The secure build still contains the demo secret.",
                        "Found in " + hit.entryName + " at offset 0x"
                                + Long.toHexString(hit.offset).toUpperCase(Locale.US));
            }
            return LabTestResult.fixed(
                    "No private API key is embedded in this APK.",
                    "Scanned: " + apk.getAbsolutePath() + "\n"
                            + "Demo key matches: 0\n"
                            + "SecretSource.apiKey(): null\n"
                            + "Public client ID: " + SecretSource.publicClientId() + "\n\n"
                            + "The backend owns the private credential and issues short-lived scoped tokens.");
        } catch (Exception e) {
            return LabTestResult.inconclusive("APK scan could not complete.",
                    e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }
}
