package com.vulndroid.app.labs.components;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.vulndroid.app.BuildConfig;
import com.vulndroid.app.R;
import com.vulndroid.app.core.DemoAccounts;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.SessionManager;
import com.vulndroid.app.core.Ui;

public final class ComponentsLabModule implements LabModule {

    @Override
    public String intro() {
        return "Android apps expose functionality through activities, services, broadcast receivers, "
                + "and content providers. The manifest decides which of them other apps may invoke. "
                + "If a sensitive component is reachable without the right permission or authorization "
                + "check, another installed app may read data or trigger actions that the user never "
                + "intended to share.\n\n"
                + "This lab uses ProfileActivity as the target. The companion VulnDroid Attacker app "
                + "launches it explicitly and receives the demo profile in the activity result.";
    }

    @Override
    public String vulnerability() {
        return "The vulnerable manifest declares ProfileActivity with android:exported=\"true\" and "
                + "no permission. The activity trusts a caller-supplied user_id, performs no session "
                + "check, and returns email, balance, and a demo bearer token to the caller.\n\n"
                + "Any installed app that knows the component name can start it with "
                + "startActivityForResult and read the result. The attacker does not need root, a "
                + "malicious link, or a victims' tap beyond installing the app. Enumeration is trivial "
                + "because IDs are small integers.\n\n"
                + "Important: an exported activity is not automatically a vulnerability. The issue is "
                + "exported access combined with sensitive data and missing authorization.";
    }

    @Override
    public String testing() {
        return "1. Install the attacker APK and open it.\n"
                + "2. Select the target package (VulnDroid or VulnDroid Secure).\n"
                + "3. Tap LAUNCH PROFILE ACTIVITY. The vulnerable build returns Alice's profile; the "
                + "secure build raises a SecurityException because the activity is not exported.\n\n"
                + "Command-line equivalents:\n\n"
                + "adb shell am start -n com.vulndroid.app/.labs.components.ProfileActivity --ei user_id 2\n"
                + "adb shell am start -n com.vulndroid.app.secure/.labs.components.ProfileActivity --ei user_id 2\n\n"
                + "Inspect the manifest without running the app:\n\n"
                + "aapt2 dump xmltree app/build/outputs/apk/vuln/debug/app-vuln-debug.apk \\\n"
                + "  --file AndroidManifest.xml | grep -A 8 ProfileActivity\n\n"
                + "adb shell dumpsys package com.vulndroid.app | grep -A 8 ProfileActivity\n\n"
                + "The retest reads the effective ActivityInfo from PackageManager rather than "
                + "trusting this screen's description.";
    }

    @Override
    public String secureImplementation() {
        return "Set android:exported=\"false\" for every component that has no legitimate external "
                + "caller. That is the primary fix here, and it is enforced by the OS before your code "
                + "runs.\n\n"
                + "If external access is genuinely required:\n"
                + "• protect the component with a narrowly scoped signature-level permission;\n"
                + "• validate every extra, URI, and action and reject unexpected input;\n"
                + "• authenticate and authorize the caller before returning data or acting;\n"
                + "• return only the minimum data, and never return bearer tokens to another app.\n\n"
                + "The secure build also ignores caller-supplied user IDs and reads only the "
                + "authenticated in-process session. Defence in depth matters because manifest "
                + "mistakes happen. A secret component name is not a security control.";
    }

    @Override
    public String summary() {
        return "Exported components expand the attack surface. Configuration tells you who can call "
                + "a component; code tells you what happens when they do. Both must be safe.\n\n"
                + "The retest uses PackageManager to read the installed manifest. Green on this lab "
                + "means the component is not exported (or is permission-protected); it does not mean "
                + "every component in the app is safe.";
    }

    @Override
    public int demoLayoutRes() {
        return R.layout.demo_components;
    }

    @Override
    public void bindDemo(View demo, Activity activity) {
        EditText userId = demo.findViewById(R.id.componentsUserId);
        TextView modeNote = demo.findViewById(R.id.componentsModeNote);
        userId.setText("1");

        demo.findViewById(R.id.componentsOpen).setOnClickListener(v -> {
            int id;
            try {
                id = Integer.parseInt(userId.getText().toString().trim());
            } catch (NumberFormatException e) {
                id = 1;
            }
            // Simulate a locally authenticated app session for the secure build.
            SessionManager.signIn(DemoAccounts.primary());
            Intent intent = new Intent(activity, ProfileActivity.class);
            intent.putExtra("user_id", id);
            activity.startActivity(intent);
        });

        demo.findViewById(R.id.componentsAttacker).setOnClickListener(v -> {
            Intent launch = activity.getPackageManager()
                    .getLaunchIntentForPackage("com.vulndroid.attacker");
            if (launch == null) {
                Toast.makeText(activity, "Install the VulnDroid Attacker APK first.",
                        Toast.LENGTH_LONG).show();
                return;
            }
            activity.startActivity(launch);
        });

        String manifest = describeManifest(activity);
        modeNote.setText((BuildConfig.VULNERABLE_BUILD
                ? "Vulnerable build: "
                : "Secure build: ") + manifest);
    }

    private String describeManifest(Activity activity) {
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(
                    new ComponentName(activity, ProfileActivity.class), 0);
            return "ProfileActivity exported=" + info.exported
                    + ", permission=" + (info.permission == null ? "none" : info.permission);
        } catch (PackageManager.NameNotFoundException e) {
            return "ProfileActivity is not declared in this build.";
        }
    }

    @Override
    public LabTestResult runTest(Activity activity) {
        try {
            ActivityInfo info = activity.getPackageManager().getActivityInfo(
                    new ComponentName(activity, ProfileActivity.class), 0);
            String component = info.packageName + "/" + info.name;
            boolean exported = info.exported;
            String permission = info.permission;

            String evidence = "Component: " + component + "\n"
                    + "android:exported=" + exported + "\n"
                    + "android:permission=" + (permission == null ? "none" : permission) + "\n"
                    + "Read via PackageManager.getActivityInfo() for the installed APK.\n\n"
                    + "External test:\n"
                    + "adb shell am start -n " + info.packageName
                    + "/.labs.components.ProfileActivity --ei user_id 2\n\n"
                    + "The companion attacker app also reads the returned profile data.";

            if (!exported) {
                return LabTestResult.fixed(
                        "ProfileActivity is not exported; external apps cannot invoke it.", evidence);
            }
            if (permission != null && !permission.trim().isEmpty()) {
                return LabTestResult.fixed(
                        "ProfileActivity is exported but protected by a permission: " + permission,
                        evidence);
            }
            return LabTestResult.vulnerable(
                    "ProfileActivity is exported without a permission and returns demo account data.",
                    evidence);
        } catch (PackageManager.NameNotFoundException e) {
            return LabTestResult.inconclusive("ProfileActivity is not declared in this build.",
                    e.getMessage());
        }
    }
}
