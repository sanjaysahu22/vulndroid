package com.vulndroid.app.labs.network;

import android.content.Context;
import android.util.Log;

import com.vulndroid.app.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * A loopback-only HTTPS endpoint with a self-signed certificate. It exists so the lab can
 * compare permissive and standard certificate validation without an external service.
 */
public final class LocalHttpsServer {

    private static final String TAG = "VulnDroidTls";
    private static final String STORE_PASSWORD = "vulndroid";
    private static final String KEY_ALIAS = "lab";

    private final Context context;
    private SSLServerSocket serverSocket;
    private Thread thread;
    private volatile boolean running;
    private int port = -1;
    private String lastError = "";

    public LocalHttpsServer(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start() throws Exception {
        if (running && serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = context.getResources().openRawResource(R.raw.lab_tls)) {
            keyStore.load(input, STORE_PASSWORD.toCharArray());
        }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            throw new IllegalStateException("lab_tls.p12 does not contain alias " + KEY_ALIAS);
        }
        KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, STORE_PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory()
                .createServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        port = serverSocket.getLocalPort();
        running = true;
        thread = new Thread(this::acceptLoop, "vulndroid-https-lab");
        thread.setDaemon(true);
        thread.start();
        lastError = "";
    }

    public int getPort() {
        return port;
    }

    public String getUrl() {
        return "https://127.0.0.1:" + port + "/api/account";
    }

    public String getLastError() {
        return lastError;
    }

    public boolean isRunning() {
        return running;
    }

    private void acceptLoop() {
        while (running) {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.setSoTimeout(5000);
                socket.startHandshake();
                handle(socket);
            } catch (SocketException e) {
                break;
            } catch (SSLException e) {
                // Expected when a correctly validating client rejects the self-signed cert.
                Log.d(TAG, "TLS handshake refused by client: " + e.getClass().getSimpleName());
            } catch (Exception e) {
                if (running) {
                    lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                    Log.w(TAG, "Loopback server error", e);
                }
            }
        }
    }

    private void handle(SSLSocket socket) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
        String line;
        int guard = 0;
        while ((line = reader.readLine()) != null && !line.isEmpty() && guard++ < 64) {
            // Consume request headers; the demo endpoint has no request body.
        }

        String body = "{\"account\":\"alice\",\"balance\":\"$1,250.00\","
                + "\"message\":\"Served over a self-signed certificate\"}";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        port = -1;
    }
}
