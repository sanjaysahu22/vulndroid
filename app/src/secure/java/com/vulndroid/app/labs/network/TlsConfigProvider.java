package com.vulndroid.app.labs.network;

import javax.net.ssl.HttpsURLConnection;

/** Hardened policy: use the platform trust store and standard hostname verification. */
public final class TlsConfigProvider {

    private TlsConfigProvider() {
    }

    public static void apply(HttpsURLConnection connection) {
        // Intentionally empty. HttpsURLConnection performs certificate-chain validation
        // and hostname verification against the platform trust anchors.
    }

    public static String description() {
        return "Android platform trust anchors and default hostname verification.";
    }
}
