package com.vulndroid.app.labs.deeplinks;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.vulndroid.app.BuildConfig;
import com.vulndroid.app.R;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

import java.util.List;

public final class DeepLinkLabModule implements LabModule {

    private static final String ADMIN_LINK = "vulndroid://admin/approve?txn=TXN-1042";
    private static final String PROFILE_LINK = "vulndroid://profile?id=2";

    @Override
    public String intro() {
        return "Deep links route a URI to an app screen. With a custom scheme such as "
                + "vulndroid://, any installed app can register the same scheme. Android may show "
                + "the user a chooser; if the attacker becomes the default handler, the victim's "
                + "links and their parameters go to the wrong app.\n\n"
                + "Links are also untrusted input. A handler that trusts host, path, or query "
                + "parameters can be tricked into exposing data or performing privileged actions.";
    }

    @Override
    public String vulnerability() {
        return "The vulnerable build registers an unverified custom scheme and exports a handler "
                + "that:\n"
                + "• accepts any scheme/host/path shape instead of allowlisting them;\n"
                + "• opens any demo profile by caller-supplied id, with no session check;\n"
                + "• approves a demo refund from vulndroid://admin/approve?txn=… without authentication "
                + "or confirmation.\n\n"
                + "The companion VulnDroid Attacker app claims the same scheme. A link intended for "
                + "the victim can therefore be delivered to the attacker, who can read its parameters "
                + "and forward a crafted link to the victim. Sensitive actions triggered solely by a "
                + "link are a serious design flaw, even when the scheme is private.";
    }

    @Override
    public String testing() {
        return "1. Simulate both presets in the demo above, with and without the session checkbox.\n"
                + "2. Trigger the handler externally:\n\n"
                + "adb shell am start -a android.intent.action.VIEW \\\n"
                + "  -d \"vulndroid://admin/approve?txn=TXN-1042\"\n\n"
                + "adb shell am start -a android.intent.action.VIEW \\\n"
                + "  -d \"vulndroid://profile?id=2\"\n\n"
                + "3. Install VulnDroid Attacker, then send the same link again. Both apps register "
                + "the scheme; choose the attacker to see it capture the URI and its parameters. Set "
                + "it as the default handler in system settings for fully automatic hijacking.\n"
                + "4. Compare with the secure build:\n\n"
                + "adb shell am start -a android.intent.action.VIEW \\\n"
                + "  -d \"https://vulndroid.example/profile?id=2\"\n\n"
                + "Real App Link verification needs https://vulndroid.example/.well-known/assetlinks.json "
                + "with the APK signing certificate's SHA-256 fingerprint. The example domain is not "
                + "registered for this lab, so use an explicit component to exercise the secure "
                + "handler locally:\n\n"
                + "adb shell am start -n com.vulndroid.app.secure/.labs.deeplinks.DeepLinkActivity \\\n"
                + "  -a android.intent.action.VIEW -d \"https://vulndroid.example/profile?id=2\"\n\n"
                + "The retest calls the actual handler with an unauthenticated admin link.";
    }

    @Override
    public String secureImplementation() {
        return "Use verified Android App Links over HTTPS, with autoVerify and a correctly served "
                + "assetlinks.json. Verify the scheme, exact host, and exact path in code as well; "
                + "verification is configuration, and configuration can fail.\n\n"
                + "Validate every parameter against an allowlist, reject unknown parameters, and never "
                + "put tokens, passwords, or other secrets in a URL. Sensitive actions must require an "
                + "authenticated session and a server-side authorization decision — never a link alone. "
                + "A confirmation step for destructive or financial actions is appropriate.\n\n"
                + "The secure build registers only https://vulndroid.example/profile and rejects "
                + "custom schemes, wrong hosts/paths, unknown parameters, out-of-range IDs, and "
                + "unauthenticated access. Admin actions are never routed from a link.";
    }

    @Override
    public String summary() {
        return "A deep link is both a routing decision and an input-validation problem. Custom "
                + "schemes are easy to hijack; App Links are the modern answer, but only when the "
                + "domain association is real and the handler still validates its input.\n\n"
                + "The retest sends the admin action to the real handler without a session. Vulnerable "
                + "means the refund is approved; fixed means the handler rejects it and the app does "
                + "not register the custom scheme.";
    }

    @Override
    public int demoLayoutRes() {
        return R.layout.demo_deeplinks;
    }

    @Override
    public void bindDemo(View demo, Activity activity) {
        EditText url = demo.findViewById(R.id.deepUrl);
        CheckBox authenticated = demo.findViewById(R.id.deepAuthenticated);
        TextView output = demo.findViewById(R.id.deepOutput);
        Button simulate = demo.findViewById(R.id.deepSimulate);

        demo.findViewById(R.id.deepPresetAdmin).setOnClickListener(v -> {
            url.setText(ADMIN_LINK);
            url.setSelection(url.getText().length());
        });
        demo.findViewById(R.id.deepPresetProfile).setOnClickListener(v -> {
            url.setText(PROFILE_LINK);
            url.setSelection(url.getText().length());
        });

        simulate.setOnClickListener(v -> {
            DeepLinkResult result = DeepLinkPolicy.handle(url.getText().toString(),
                    authenticated.isChecked());
            output.setTextColor(result.approvedAction
                    ? Ui.color(activity, R.color.vd_red)
                    : Ui.color(activity, R.color.vd_primary));
            output.setText("RESULT: " + result.title + "\n\n"
                    + result.message + "\n\n"
                    + "DATA:\n" + (result.exposedData.isEmpty() ? "none" : result.exposedData) + "\n\n"
                    + "CHECKS:\n" + result.checks);
        });
    }

    @Override
    public LabTestResult runTest(Activity activity) {
        DeepLinkResult result = DeepLinkPolicy.handle(ADMIN_LINK, false);
        int ownHandlers = countOwnHandlers(activity, ADMIN_LINK);

        String evidence = "Link: " + ADMIN_LINK + "\n"
                + "Authenticated session: false\n"
                + "Handler outcome: " + result.title + "\n"
                + "Privileged action approved: " + result.approvedAction + "\n"
                + "Own custom-scheme handlers for vulndroid://: " + ownHandlers + "\n"
                + "Policy checks: " + result.checks;

        if (result.approvedAction) {
            return LabTestResult.vulnerable(
                    "An unauthenticated deep link approved a demo refund.", evidence);
        }
        if (result.handled && !result.approvedAction) {
            return LabTestResult.fixed(
                    "The URI policy rejected the unauthenticated admin action and no custom-scheme "
                            + "handler is registered.", evidence);
        }
        return LabTestResult.inconclusive("The URI handler did not return a usable decision.", evidence);
    }

    private int countOwnHandlers(Activity activity, String link) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            List<ResolveInfo> handlers = activity.getPackageManager().queryIntentActivities(intent, 0);
            int count = 0;
            if (handlers != null) {
                for (ResolveInfo info : handlers) {
                    if (info.activityInfo != null
                            && activity.getPackageName().equals(info.activityInfo.packageName)) {
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }
}
