package com.vulndroid.app.labs.secrets;

/**
 * DELIBERATELY INSECURE training implementation.
 * This is a fake key, not a credential. The point is that any client-side secret is
 * recoverable from the APK with `strings`, JADX, or MobSF.
 */
public final class SecretSource {

    public static final String DEMO_API_KEY = "DEMO_KEY_NOT_VALID_12345";

    private SecretSource() {
    }

    public static String apiKey() {
        return DEMO_API_KEY;
    }

    public static String publicClientId() {
        return null;
    }

    public static String modeDescription() {
        return "Private API key embedded in the APK.";
    }
}
