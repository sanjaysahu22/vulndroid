package com.vulndroid.app.labs.network;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLException;

public final class LabTlsClient {

    private LabTlsClient() {
    }

    public static TlsFetchResult fetch(String url) {
        TlsFetchResult result = new TlsFetchResult();
        result.policy = TlsConfigProvider.description();
        HttpsURLConnection connection = null;
        try {
            URL target = new URL(url);
            if (!"https".equalsIgnoreCase(target.getProtocol())) {
                result.errorType = "Unsupported scheme";
                result.errorMessage = "The lab only performs HTTPS requests.";
                return result;
            }
            connection = (HttpsURLConnection) target.openConnection();
            TlsConfigProvider.apply(connection);
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-VulnDroid-Client", "lab");

            int code = connection.getResponseCode();
            result.statusCode = code;
            result.success = code > 0;
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            result.body = readBody(stream);
            try {
                result.cipherSuite = connection.getCipherSuite();
                result.peer = String.valueOf(connection.getPeerPrincipal());
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            result.errorType = e.getClass().getSimpleName();
            result.errorMessage = e.getMessage() == null ? e.toString() : e.getMessage();
            result.certificateRejected = isCertificateError(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    private static boolean isCertificateError(Throwable throwable) {
        Throwable current = throwable;
        int guard = 0;
        while (current != null && guard++ < 12) {
            if (current instanceof CertPathValidatorException
                    || current instanceof CertificateException) {
                return true;
            }
            if (current instanceof SSLHandshakeException) {
                String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
                if (message.contains("cert") || message.contains("trust")
                        || message.contains("hostname") || current.getCause() != null) {
                    return true;
                }
            }
            if (current instanceof SSLException) {
                String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
                if (message.contains("cert") || message.contains("trust")
                        || message.contains("hostname")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static String readBody(InputStream input) {
        if (input == null) {
            return "";
        }
        try (InputStream stream = input) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > 64 * 1024) {
                    break;
                }
            }
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "(could not read response body: " + e.getMessage() + ")";
        }
    }
}
