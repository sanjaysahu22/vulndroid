package com.vulndroid.app.labs.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.vulndroid.app.core.DemoAccounts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Hardened storage strategy: only a Keystore-encrypted, short-lived demo session blob is
 * persisted. Account details stay in memory and legacy plaintext artifacts are removed.
 */
public final class StorageLabOps {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "vulndroid_session_key";
    private static final String SECURE_PREFS = "secure_session";
    private static final String BLOB_KEY = "blob";
    private static final long MAX_SCAN_FILE = 8L * 1024L * 1024L;

    private StorageLabOps() {
    }

    public static StorageResults.LoginResult signIn(Context context, String username, String password) {
        StorageResults.LoginResult result = new StorageResults.LoginResult();
        DemoAccounts.Account account = DemoAccounts.authenticate(username, password);
        if (account == null) {
            result.message = "Invalid demo credentials. Use alice / demo123.";
            return result;
        }
        try {
            wipeLegacyArtifacts(context);
            String payload = "{\"u\":\"" + account.username + "\",\"t\":\"" + account.token
                    + "\",\"exp\":" + (System.currentTimeMillis() + 30 * 60 * 1000L) + "}";
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String blob = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ciphertext, Base64.NO_WRAP);

            context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(BLOB_KEY, blob)
                    .putString("key_alias", KEY_ALIAS)
                    .putLong("stored_at", System.currentTimeMillis())
                    .putInt("ttl_seconds", 1800)
                    .commit();

            result.success = true;
            result.account = account;
            result.token = account.token;
            result.encrypted = true;
            result.storageDetail = "Only AES-GCM ciphertext is persisted. The non-exportable key "
                    + "lives in Android Keystore; email and balance remain in memory.";
            result.message = "Signed in. No plaintext token or PII written to disk.";
            return result;
        } catch (Exception e) {
            result.message = "Secure session setup failed: " + e.getClass().getSimpleName()
                    + " — " + e.getMessage();
            return result;
        }
    }

    public static StorageResults.ScanResult scan(Context context) {
        StorageResults.ScanResult result = new StorageResults.ScanResult();
        List<byte[]> needles = new ArrayList<>();
        for (DemoAccounts.Account account : DemoAccounts.all()) {
            needles.add(account.token.getBytes(StandardCharsets.UTF_8));
        }
        scanTree(context.getDataDir(), needles, result.hits);
        result.tokenFound = !result.hits.isEmpty();

        SharedPreferences secure = context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE);
        String blob = secure.getString(BLOB_KEY, null);
        result.secureBlobPresent = blob != null;
        if (blob != null) {
            try {
                String decrypted = decrypt(blob);
                result.decryptOk = decrypted.contains("DEMO_TOKEN_");
                result.cryptoDetail = "Android Keystore key " + KEY_ALIAS
                        + " decrypted the AES-GCM blob successfully. The blob is useless without "
                        + "the device-bound key.";
            } catch (Exception e) {
                result.cryptoDetail = "Encrypted blob present but decryption failed: "
                        + e.getClass().getSimpleName();
            }
        } else {
            result.cryptoDetail = "No secure session blob found. Sign in first.";
        }

        result.summary = result.tokenFound
                ? "Plaintext demo token found in " + result.hits.size() + " file(s)."
                : "No plaintext demo token found. Persisted data is Keystore-encrypted ciphertext.";
        return result;
    }

    public static void clearAll(Context context) {
        wipeLegacyArtifacts(context);
        context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static void wipeLegacyArtifacts(Context context) {
        context.getSharedPreferences("user_data", Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteSharedPreferences("user_data");
        context.deleteDatabase("vulndroid.db");
        File session = new File(context.getFilesDir(), "session.txt");
        if (session.exists()) {
            //noinspection ResultOfMethodCallIgnored
            session.delete();
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static String decrypt(String blob) throws Exception {
        String[] parts = blob.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed blob");
        }
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private static void scanTree(File file, List<byte[]> needles, List<String> hits) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                String name = child.getName();
                if (name.equals("lib") || name.equals("code_cache")) {
                    continue;
                }
                scanTree(child, needles, hits);
            }
            return;
        }
        if (file.length() > MAX_SCAN_FILE) {
            return;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            for (byte[] needle : needles) {
                if (contains(data, needle, read)) {
                    hits.add(file.getAbsolutePath());
                    break;
                }
            }
        } catch (IOException ignored) {
            // Skip unreadable files.
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle, int length) {
        outer:
        for (int i = 0; i <= length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
