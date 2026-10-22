package com.vulndroid.app.labs.secrets;

/**
 * Hardened design: no private credential ships in the APK. Only a public client identifier
 * is present; the backend owns the private key and issues short-lived, scoped tokens.
 */
public final class SecretSource {

    public static final String PUBLIC_CLIENT_ID = "vulndroid-public-client";

    private SecretSource() {
    }

    public static String apiKey() {
        return null;
    }

    public static String publicClientId() {
        return PUBLIC_CLIENT_ID;
    }

    public static String modeDescription() {
        return "No private key in the APK. Only a public client identifier is present.";
    }
}
