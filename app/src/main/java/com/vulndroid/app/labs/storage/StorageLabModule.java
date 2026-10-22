package com.vulndroid.app.labs.storage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.BuildConfig;
import com.vulndroid.app.R;
import com.vulndroid.app.core.DemoAccounts;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

public final class StorageLabModule implements LabModule {

    @Override
    public String intro() {
        return "Apps persist sessions so users stay signed in. If that state is written as plaintext "
                + "inside the app sandbox, anyone who can reach the filesystem — a rooted device, a "
                + "debuggable build, a backup, or a forensic image — can read it.\n\n"
                + "This lab signs in with a fake account and writes the same session to three classic "
                + "weak locations. Then it scans for the token as an attacker would.";
    }

    @Override
    public String vulnerability() {
        return "The vulnerable build stores the demo bearer token in:\n"
                + "• SharedPreferences — shared_prefs/user_data.xml\n"
                + "• SQLite — databases/vulndroid.db, table sessions\n"
                + "• A private file — files/session.txt\n\n"
                + "It also writes the token to logcat. App-private storage is only private while the "
                + "sandbox holds. adb run-as on a debuggable build, root, a backup, or malware with "
                + "device access breaks that boundary. A recovered bearer token can be replayed to "
                + "reach the account without the password, and the same files expose email and balance.";
    }

    @Override
    public String testing() {
        return "1. Sign in as alice / demo123, then open the storage inspector.\n"
                + "2. Look for the demo token in each artifact.\n"
                + "3. Reproduce it outside the app on a debug build:\n\n"
                + "adb shell run-as com.vulndroid.app ls -R .\n"
                + "adb shell run-as com.vulndroid.app cat shared_prefs/user_data.xml\n"
                + "adb shell run-as com.vulndroid.app cat files/session.txt\n"
                + "adb exec-out run-as com.vulndroid.app cat databases/vulndroid.db > vulndroid.db\n\n"
                + "4. In MobSF, use the dynamic analysis file explorer, or pull the same paths.\n"
                + "5. Repeat against com.vulndroid.app.secure and compare.\n\n"
                + "Note: run-as only works while the APK is debuggable. On a production build you "
                + "need root or another way into the sandbox — but the insecure storage is still the "
                + "root cause when that access occurs.";
    }

    @Override
    public String secureImplementation() {
        return "The secure build keeps only a short-lived, AES-GCM-encrypted session blob and a "
                + "non-exportable key in Android Keystore. Email and balance stay in memory; the "
                + "legacy prefs, database, and file are removed on sign-in.\n\n"
                + "KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,\n"
                + "        PURPOSE_ENCRYPT | PURPOSE_DECRYPT)\n"
                + "    .setBlockModes(BLOCK_MODE_GCM)\n"
                + "    .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)\n"
                + "    .build();\n\n"
                + "That is not the whole fix. Minimise what you retain, expire sessions, revoke them "
                + "server-side, and avoid writing tokens at all when memory is enough. Keystore "
                + "protects the key from extraction and makes stolen ciphertext useless off-device; "
                + "it cannot stop a live process on a fully compromised unlocked device.";
    }

    @Override
    public String summary() {
        return "Insecure storage is about reachability, not just file permissions. The vulnerable "
                + "build leaves a reusable token in three ordinary places; the secure build leaves "
                + "ciphertext that cannot be decrypted without a device-bound Keystore key.\n\n"
                + "Retest honestly: run the scan after signing in. A green status means the scanner "
                + "found no plaintext token and the encrypted session still decrypts correctly.";
    }

    @Override
    public int demoLayoutRes() {
        return R.layout.demo_storage;
    }

    @Override
    public void bindDemo(View demo, Activity activity) {
        EditText username = demo.findViewById(R.id.storageUsername);
        EditText password = demo.findViewById(R.id.storagePassword);
        Button signIn = demo.findViewById(R.id.storageSignIn);
        TextView message = demo.findViewById(R.id.storageLoginMessage);
        LinearLayout profileCard = demo.findViewById(R.id.storageProfileCard);
        TextView name = demo.findViewById(R.id.storageProfileName);
        TextView email = demo.findViewById(R.id.storageProfileEmail);
        TextView balance = demo.findViewById(R.id.storageProfileBalance);
        TextView token = demo.findViewById(R.id.storageProfileToken);
        TextView note = demo.findViewById(R.id.storageProfileNote);

        username.setText("alice");
        password.setText("demo123");

        signIn.setOnClickListener(v -> {
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            signIn.setEnabled(false);
            message.setText("Signing in and writing the demo session…");
            message.setTextColor(Ui.color(activity, R.color.vd_text_muted));
            new Thread(() -> {
                StorageResults.LoginResult result = StorageLabOps.signIn(activity, user, pass);
                activity.runOnUiThread(() -> {
                    signIn.setEnabled(true);
                    if (!result.success) {
                        message.setText(result.message);
                        message.setTextColor(Ui.color(activity, R.color.vd_red));
                        profileCard.setVisibility(View.GONE);
                        return;
                    }
                    message.setText(result.message);
                    message.setTextColor(result.encrypted
                            ? Ui.color(activity, R.color.vd_green)
                            : Ui.color(activity, R.color.vd_red));
                    profileCard.setVisibility(View.VISIBLE);
                    name.setText(result.account.displayName + "  ·  " + result.account.role);
                    email.setText(result.account.email);
                    balance.setText("Demo balance " + result.account.balance);
                    token.setText(result.encrypted
                            ? result.account.token + "\n(in memory only; disk has ciphertext)"
                            : result.account.token);
                    note.setText(result.storageDetail);
                    note.setTextColor(result.encrypted
                            ? Ui.color(activity, R.color.vd_green)
                            : Ui.color(activity, R.color.vd_amber));
                });
            }).start();
        });

        demo.findViewById(R.id.storageInspect).setOnClickListener(v -> {
            Intent intent = new Intent(activity, StorageInspectorActivity.class);
            activity.startActivity(intent);
        });

        TextView caption = new TextView(activity);
        caption.setText(BuildConfig.VULNERABLE_BUILD
                ? "This APK contains the vulnerable implementation. Install the secure flavor to compare."
                : "This APK contains the hardened implementation. The inspector should show ciphertext, not a token.");
        caption.setTextSize(11f);
        caption.setTypeface(Typeface.MONOSPACE);
        caption.setTextColor(Ui.color(activity, R.color.vd_text_faint));
        caption.setPadding(0, Ui.dp(activity, 10), 0, 0);
        ((LinearLayout) demo).addView(caption);
    }

    @Override
    public LabTestResult runTest(Activity activity) {
        StorageLabOps.clearAll(activity);
        DemoAccounts.Account account = DemoAccounts.primary();
        StorageResults.LoginResult login = StorageLabOps.signIn(activity, account.username, account.password);
        if (!login.success) {
            return LabTestResult.inconclusive("Demo sign-in failed before the storage scan.",
                    login.message);
        }
        StorageResults.ScanResult scan = StorageLabOps.scan(activity);

        if (scan.tokenFound) {
            StringBuilder evidence = new StringBuilder();
            evidence.append("Demo token: ").append(account.token).append("\n");
            evidence.append("Recovered from:\n");
            for (String hit : scan.hits) {
                evidence.append("• ").append(hit).append("\n");
            }
            evidence.append("\nReproduce:\n")
                    .append("adb shell run-as ").append(activity.getPackageName())
                    .append(" cat shared_prefs/user_data.xml\n")
                    .append("adb shell run-as ").append(activity.getPackageName())
                    .append(" cat files/session.txt");
            return LabTestResult.vulnerable(
                    "Plaintext demo token recovered from app-private storage.", evidence.toString());
        }

        if (scan.secureBlobPresent && scan.decryptOk) {
            return LabTestResult.fixed(
                    "No plaintext token on disk; the Keystore-encrypted session decrypts correctly.",
                    "Scanned: " + activity.getDataDir().getAbsolutePath() + "\n"
                            + "Plaintext hits: 0\n"
                            + scan.cryptoDetail + "\n"
                            + "Legacy SharedPreferences, SQLite session table and files/session.txt were removed.");
        }

        return LabTestResult.inconclusive(
                "No plaintext token was found, but the encrypted session could not be verified.",
                scan.summary + "\n" + scan.cryptoDetail);
    }
}
