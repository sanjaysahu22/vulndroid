package com.vulndroid.app.labs.components;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.BuildConfig;
import com.vulndroid.app.R;
import com.vulndroid.app.core.DemoAccounts;
import com.vulndroid.app.core.SessionManager;
import com.vulndroid.app.core.Ui;

public class ProfileActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView banner = findViewById(R.id.profileBanner);
        LinearLayout headerCard = findViewById(R.id.profileHeaderCard);
        TextView name = findViewById(R.id.profileName);
        TextView email = findViewById(R.id.profileEmail);
        TextView role = findViewById(R.id.profileRole);
        TextView balance = findViewById(R.id.profileBalance);
        TextView token = findViewById(R.id.profileToken);
        TextView caller = findViewById(R.id.profileCaller);
        TextView resultNote = findViewById(R.id.profileResultNote);
        TextView note = findViewById(R.id.profileNote);
        findViewById(R.id.profileClose).setOnClickListener(v -> finish());

        int requestedId = getIntent().getIntExtra("user_id",
                getIntent().getIntExtra("id", 1));
        DemoAccounts.Account requested = DemoAccounts.byId(requestedId);
        if (requested == null) {
            requested = DemoAccounts.primary();
        }

        String launchedFrom = null;
        try {
            launchedFrom = getLaunchedFromPackage();
        } catch (Exception ignored) {
        }
        String callerText = "launched_from=" + (launchedFrom == null ? "unknown" : launchedFrom)
                + "\ncalling_activity=" + (getCallingActivity() == null
                        ? "none (startActivity, no result)"
                        : getCallingActivity().flattenToShortString());
        caller.setText(callerText);

        boolean secure = !BuildConfig.VULNERABLE_BUILD;
        DemoAccounts.Account account = requested;

        if (secure && !SessionManager.isSignedIn()) {
            banner.setText("ACCESS DENIED  ·  no authenticated session");
            banner.setTextColor(Ui.color(this, R.color.vd_red));
            banner.setBackground(Ui.rounded(Ui.color(this, R.color.vd_red_bg), 10f, this));
            headerCard.setVisibility(View.GONE);
            note.setText("The secure build requires an in-process authenticated session before it "
                    + "displays profile data. The activity is also not exported, so another app "
                    + "cannot invoke it at all. Both controls matter: authentication protects the "
                    + "data, and the manifest reduces the attack surface.");
            resultNote.setText("Result: RESULT_CANCELED (authorization check failed)");
            if (getCallingActivity() != null) {
                setResult(RESULT_CANCELED);
            }
            return;
        }

        if (secure) {
            account = SessionManager.current();
            if (account == null) {
                finish();
                return;
            }
            banner.setText("SECURE COMPONENT  ·  session-backed profile");
            banner.setTextColor(Ui.color(this, R.color.vd_green));
            banner.setBackground(Ui.rounded(Ui.color(this, R.color.vd_green_bg), 10f, this));
            note.setText("This build ignores caller-supplied user IDs and reads the authenticated "
                    + "in-process session instead. The manifest sets android:exported=\"false\", "
                    + "so components from other apps cannot reach it. If external access were a "
                    + "real requirement, a signature-level permission, input validation and a "
                    + "server-side authorization check would still be required.");
            token.setText("not displayed · session token stays in memory");
        } else {
            banner.setText("EXPORTED COMPONENT  ·  no authorization check");
            banner.setTextColor(Ui.color(this, R.color.vd_red));
            banner.setBackground(Ui.rounded(Ui.color(this, R.color.vd_red_bg), 10f, this));
            note.setText("This build trusts the caller-supplied user_id, performs no authentication "
                    + "check, and is exported. An installed app can request any demo account and "
                    + "receive its profile data in the activity result. An exported component is not "
                    + "automatically a vulnerability — the unsafe access to data is what makes it one.");
            token.setText(account.token);
        }

        name.setText(account.displayName);
        email.setText(account.email);
        role.setText(account.role);
        balance.setText(account.balance);

        if (getCallingActivity() != null) {
            Intent result = new Intent();
            if (secure) {
                result.putExtra("profile", account.displayName + " · " + account.role);
                result.putExtra("token", "not returned by secure build");
            } else {
                result.putExtra("profile", account.summary());
                result.putExtra("token", account.token);
            }
            setResult(RESULT_OK, result);
            resultNote.setText("Result: RESULT_OK returned to " + getCallingActivity().getPackageName());
        } else {
            resultNote.setText("No result receiver. The profile is still visible to whoever launched it.");
        }
    }
}
