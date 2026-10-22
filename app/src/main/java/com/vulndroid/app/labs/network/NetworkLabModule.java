package com.vulndroid.app.labs.network;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.vulndroid.app.BuildConfig;
import com.vulndroid.app.R;
import com.vulndroid.app.core.LabModule;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

public final class NetworkLabModule implements LabModule {

    private LocalHttpsServer server;

    @Override
    public String intro() {
        return "TLS does two jobs: it encrypts traffic and it proves the server's identity. A client "
                + "that accepts any certificate keeps the encryption but throws away authentication. "
                + "Any party able to intercept the connection — a proxy, hostile Wi-Fi, or malware with "
                + "device access — can present its own certificate and read or modify the traffic.\n\n"
                + "This lab starts a loopback HTTPS server with a self-signed certificate. The secure "
                + "client should reject it; the vulnerable client accepts it.";
    }

    @Override
    public String vulnerability() {
        return "The vulnerable build installs a trust-all X509TrustManager and a hostname verifier "
                + "that returns true for every host. The request succeeds even though the certificate "
                + "is self-signed and not trusted by Android.\n\n"
                + "That is exactly what a man-in-the-middle needs. With Burp Suite or any intercepting "
                + "proxy, an attacker can terminate TLS with their own certificate, read credentials and "
                + "tokens, and alter responses. Hostname verification being disabled also means a valid "
                + "certificate for a different host is accepted.\n\n"
                + "This code is isolated to the lab screen. Copying it into a real app's global "
                + "networking stack would be a serious production vulnerability.";
    }

    @Override
    public String testing() {
        return "1. Tap SEND HTTPS REQUEST. The vulnerable build returns HTTP 200 from the self-signed "
                + "endpoint; the secure build fails during the TLS handshake.\n"
                + "2. Test with Burp Suite:\n\n"
                + "adb shell settings put global http_proxy 10.0.2.2:8080\n"
                + "# Browse/send the request, then remove the proxy:\n"
                + "adb shell settings put global http_proxy :0\n\n"
                + "3. Burp listens on all interfaces. The vulnerable build accepts Burp's certificate "
                + "without installing a CA. The secure build rejects the untrusted chain (targetSdk 34 "
                + "does not trust user-installed CAs by default). A debug-only network security config "
                + "can opt into a user CA for testing; do not ship that override.\n"
                + "4. Inspect the certificate independently:\n\n"
                + "openssl s_client -connect 127.0.0.1:8443 -servername localhost\n\n"
                + "The bundled tools/https_test_server.py provides the same endpoint for desktop-side "
                + "experiments.";
    }

    @Override
    public String secureImplementation() {
        return "Use the platform's standard TLS validation: HttpsURLConnection defaults, OkHttp with "
                + "its default trust chain, or a correctly configured network security policy. Never "
                + "use trust-all trust managers or permissive hostname verifiers, even in debug code "
                + "that might ship.\n\n"
                + "If you need a custom CA, scope it explicitly with a network security configuration "
                + "and restrict it to the domains that need it. If you pin certificates, plan key "
                + "rotation and a recovery path; pinning without rotation can brick clients. Pinning "
                + "is optional, not a substitute for validating the chain and hostname.\n\n"
                + "The secure build calls the same request code with no custom TLS policy, so Android "
                + "rejects the self-signed certificate. That rejection is the proof the control works.";
    }

    @Override
    public String summary() {
        return "HTTPS without certificate validation is only obfuscation. The vulnerable build "
                + "accepts an untrusted certificate and would accept an intercepting proxy; the secure "
                + "build fails the handshake.\n\n"
                + "Green here means the client rejected a deliberately untrusted certificate. It does "
                + "not prove the whole app uses safe TLS everywhere — review the networking stack and "
                + "any debug overrides as part of the retest.";
    }

    @Override
    public int demoLayoutRes() {
        return R.layout.demo_network;
    }

    @Override
    public void bindDemo(View demo, Activity activity) {
        TextView endpoint = demo.findViewById(R.id.networkEndpoint);
        TextView output = demo.findViewById(R.id.networkOutput);
        TextView note = demo.findViewById(R.id.networkModeNote);
        Button send = demo.findViewById(R.id.networkSend);

        server = new LocalHttpsServer(activity);
        try {
            server.start();
            endpoint.setText(server.getUrl());
        } catch (Exception e) {
            endpoint.setText("Local TLS server failed to start: " + e.getClass().getSimpleName()
                    + " — " + e.getMessage());
        }

        note.setText((BuildConfig.VULNERABLE_BUILD
                ? "This build uses the lab's trust-all client: the self-signed certificate should be accepted."
                : "This build uses Android's standard TLS validation: the self-signed certificate should be rejected.")
                + " The server binds to loopback only and is stopped when you leave the lab.");

        send.setOnClickListener(v -> {
            if (server == null || !server.isRunning() || server.getPort() <= 0) {
                output.setVisibility(View.VISIBLE);
                output.setText("Server is not running. Reopen the lab.");
                return;
            }
            send.setEnabled(false);
            output.setVisibility(View.VISIBLE);
            output.setText("Performing TLS handshake and request…");
            String url = server.getUrl();
            new Thread(() -> {
                TlsFetchResult result = LabTlsClient.fetch(url);
                activity.runOnUiThread(() -> {
                    send.setEnabled(true);
                    output.setText(formatResult(result, url));
                    if (result.success) {
                        output.setTextColor(Ui.color(activity, R.color.vd_red));
                    } else if (result.certificateRejected) {
                        output.setTextColor(Ui.color(activity, R.color.vd_green));
                    } else {
                        output.setTextColor(Ui.color(activity, R.color.vd_amber));
                    }
                });
            }).start();
        });
    }

    private String formatResult(TlsFetchResult result, String url) {
        StringBuilder out = new StringBuilder();
        out.append("GET ").append(url).append("\n\n");
        if (result.success) {
            out.append("HTTP ").append(result.statusCode).append("\n")
                    .append(result.body).append("\n\n");
            if (result.cipherSuite != null && !result.cipherSuite.isEmpty()) {
                out.append("Cipher: ").append(result.cipherSuite).append("\n");
            }
            if (result.peer != null && !result.peer.isEmpty() && !"null".equals(result.peer)) {
                out.append("Peer: ").append(result.peer).append("\n");
            }
            out.append("\nThe self-signed certificate was ACCEPTED.\n")
                    .append("Policy: ").append(result.policy);
        } else if (result.certificateRejected) {
            out.append("TLS handshake failed as expected.\n")
                    .append(result.errorType).append(": ").append(result.errorMessage).append("\n\n")
                    .append("The untrusted certificate was REJECTED.\n")
                    .append("Policy: ").append(result.policy);
        } else {
            out.append("Request did not complete.\n")
                    .append(result.errorType).append(": ").append(result.errorMessage).append("\n")
                    .append("This can be a connection issue rather than a TLS result.");
        }
        return out.toString();
    }

    @Override
    public LabTestResult runTest(Activity activity) {
        try {
            if (server == null) {
                server = new LocalHttpsServer(activity);
            }
            if (!server.isRunning() || server.getPort() <= 0) {
                server.start();
            }
        } catch (Exception e) {
            return LabTestResult.inconclusive("The loopback HTTPS server could not start.",
                    e.getClass().getSimpleName() + " — " + e.getMessage());
        }

        String url = server.getUrl();
        TlsFetchResult result = LabTlsClient.fetch(url);
        String evidence = "Endpoint: " + url + "\n"
                + "Certificate: self-signed for 127.0.0.1, absent from Android's trust store\n"
                + "Policy: " + result.policy + "\n"
                + "HTTP status: " + (result.success ? result.statusCode : "none") + "\n"
                + "Cipher: " + (result.cipherSuite == null || result.cipherSuite.isEmpty()
                        ? "n/a" : result.cipherSuite) + "\n"
                + "Error: " + (result.success ? "none"
                        : result.errorType + " — " + result.errorMessage) + "\n"
                + "Certificate rejected: " + result.certificateRejected;

        if (result.success) {
            return LabTestResult.vulnerable(
                    "The client accepted a self-signed certificate and completed the HTTPS request.",
                    evidence + "\n\nResponse:\n" + result.body);
        }
        if (result.certificateRejected) {
            return LabTestResult.fixed(
                    "Standard certificate validation rejected the self-signed endpoint.", evidence);
        }
        return LabTestResult.inconclusive(
                "The request failed, but not because of certificate validation.", evidence);
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}
