package com.vulndroid.app.labs.network;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * DELIBERATELY INSECURE, LAB-ONLY TLS POLICY.
 * It accepts every certificate and every hostname, which defeats the purpose of TLS and
 * makes interception trivial. The vulnerable build applies it only to the isolated network
 * lab, never to ordinary application traffic. Never ship code like this.
 */
public final class TlsConfigProvider {

    private TlsConfigProvider() {
    }

    public static void apply(HttpsURLConnection connection) throws Exception {
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }};

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, trustAll, new SecureRandom());
        connection.setSSLSocketFactory(context.getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> true);
    }

    public static String description() {
        return "Trust-all X509TrustManager and hostname verifier (lab only).";
    }
}
