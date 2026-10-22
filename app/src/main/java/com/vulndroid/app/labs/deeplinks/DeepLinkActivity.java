package com.vulndroid.app.labs.deeplinks;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.vulndroid.app.BuildConfig;
import com.vulndroid.app.R;
import com.vulndroid.app.core.DemoAccounts;
import com.vulndroid.app.core.SessionManager;
import com.vulndroid.app.core.Ui;

public class DeepLinkActivity extends Activity {

    private String url;
    private TextView banner;
    private TextView incoming;
    private TextView title;
    private TextView message;
    private TextView exposed;
    private TextView checks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deep_link);

        banner = findViewById(R.id.deepBanner);
        incoming = findViewById(R.id.deepIncomingUrl);
        title = findViewById(R.id.deepResultTitle);
        message = findViewById(R.id.deepResultMessage);
        exposed = findViewById(R.id.deepExposed);
        checks = findViewById(R.id.deepChecks);

        findViewById(R.id.deepClose).setOnClickListener(v -> finish());
        findViewById(R.id.deepDone).setOnClickListener(v -> finish());

        url = getIntent().getDataString();
        if (url == null || url.isEmpty()) {
            url = "vulndroid://profile?id=2";
        }
        render(url, SessionManager.isSignedIn());

        findViewById(R.id.deepRetrySignedIn).setOnClickListener(v -> {
            SessionManager.signIn(DemoAccounts.primary());
            Toast.makeText(this, "Demo session marked authenticated in memory.",
                    Toast.LENGTH_SHORT).show();
            render(url, true);
        });
    }

    private void render(String uri, boolean authenticated) {
        incoming.setText(uri);
        DeepLinkResult result = DeepLinkPolicy.handle(uri, authenticated);

        banner.setText(BuildConfig.VULNERABLE_BUILD
                ? "VULNERABLE HANDLER  ·  unverified custom scheme"
                : "HARDENED POLICY  ·  verified HTTPS App Link rules");
        banner.setTextColor(Ui.color(this, BuildConfig.VULNERABLE_BUILD
                ? R.color.vd_red : R.color.vd_green));
        banner.setBackground(Ui.rounded(Ui.color(this, BuildConfig.VULNERABLE_BUILD
                ? R.color.vd_red_bg : R.color.vd_green_bg), 10f, this));

        title.setText(result.title);
        int titleColor = result.approvedAction
                ? Ui.color(this, R.color.vd_red)
                : (BuildConfig.VULNERABLE_BUILD
                        ? Ui.color(this, R.color.vd_amber)
                        : Ui.color(this, R.color.vd_green));
        title.setTextColor(titleColor);
        message.setText(result.message);
        exposed.setText(result.exposedData.isEmpty() ? "None." : result.exposedData);
        checks.setText(result.checks + "\n\nSession present: " + authenticated
                + "\nBuild: " + (BuildConfig.VULNERABLE_BUILD ? "vulnerable" : "secure"));
        findViewById(R.id.deepRetrySignedIn).setVisibility(
                BuildConfig.VULNERABLE_BUILD ? View.GONE : View.VISIBLE);
    }
}
