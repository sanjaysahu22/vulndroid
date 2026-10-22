package com.vulndroid.app.labs.storage;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.vulndroid.app.core.DemoAccounts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DELIBERATELY INSECURE training implementation. The demo token and demo PII are written
 * in plaintext to SharedPreferences, a SQLite database and a private file. Never copy this
 * pattern into a real app.
 */
public final class StorageLabOps {

    public static final String TOKEN_PREFS = "user_data";
    public static final String DB_NAME = "vulndroid.db";
    public static final String SESSION_FILE = "session.txt";

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

        SharedPreferences preferences = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE);
        // commit() (not apply()) so the XML file is on disk when the scanner runs. The
        // weakness being demonstrated is that the token is stored as plaintext at all,
        // not when the write happens.
        preferences.edit()
                .putString("username", account.username)
                .putString("demo_token", account.token)
                .putString("email", account.email)
                .putString("balance", account.balance)
                .putBoolean("logged_in", true)
                .commit();

        SQLiteDatabase database = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null);
        try {
            database.execSQL("CREATE TABLE IF NOT EXISTS sessions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT, token TEXT, email TEXT, created_at TEXT)");
            database.delete("sessions", null, null);
            ContentValues values = new ContentValues();
            values.put("username", account.username);
            values.put("token", account.token);
            values.put("email", account.email);
            values.put("created_at", String.valueOf(System.currentTimeMillis()));
            database.insertOrThrow("sessions", null, values);
        } finally {
            database.close();
        }

        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), SESSION_FILE))) {
            String body = "username=" + account.username + "\n"
                    + "demo_token=" + account.token + "\n"
                    + "email=" + account.email + "\n"
                    + "balance=" + account.balance + "\n";
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            result.message = "File write failed: " + e.getMessage();
        }

        // Logcat is another classic leak. This is intentionally visible in the vulnerable lab.
        Log.w("VulnDroidLab", "Demo session issued. token=" + account.token + " user=" + account.username);

        result.success = true;
        result.account = account;
        result.token = account.token;
        result.encrypted = false;
        result.storageDetail = "Wrote the token to SharedPreferences (user_data.xml), SQLite "
                + "(vulndroid.db / sessions), and files/session.txt. Also logged it to logcat.";
        result.message = "Signed in. Plaintext session data written to three locations.";
        return result;
    }

    public static StorageResults.ScanResult scan(Context context) {
        StorageResults.ScanResult result = new StorageResults.ScanResult();
        File dataDir = context.getDataDir();
        List<byte[]> needles = new ArrayList<>();
        StringBuilder tokenList = new StringBuilder();
        for (DemoAccounts.Account account : DemoAccounts.all()) {
            needles.add(account.token.getBytes(StandardCharsets.UTF_8));
            if (tokenList.length() > 0) {
                tokenList.append(", ");
            }
            tokenList.append(account.token);
        }

        scanTree(context, dataDir, needles, result.hits);

        result.tokenFound = !result.hits.isEmpty();
        result.secureBlobPresent = context.getSharedPreferences("secure_session", Context.MODE_PRIVATE)
                .contains("blob");
        result.cryptoDetail = "Vulnerable build: no encryption. Tokens stored as readable text.";
        result.summary = result.tokenFound
                ? "Plaintext demo tokens were found in " + result.hits.size() + " app-private file(s)."
                : "No plaintext demo token was found under " + dataDir.getAbsolutePath() + ".";
        return result;
    }

    public static void clearAll(Context context) {
        context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteSharedPreferences(TOKEN_PREFS);
        context.deleteDatabase(DB_NAME);
        File session = new File(context.getFilesDir(), SESSION_FILE);
        if (session.exists()) {
            //noinspection ResultOfMethodCallIgnored
            session.delete();
        }
    }

    private static void scanTree(Context context, File file, List<byte[]> needles, List<String> hits) {
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
                if (name.equals("lib") || name.equals("code_cache") || name.equals("no_backup")) {
                    continue;
                }
                scanTree(context, child, needles, hits);
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
                    String path = file.getAbsolutePath();
                    if (!hits.contains(path)) {
                        hits.add(path);
                    }
                    break;
                }
            }
        } catch (IOException ignored) {
            // Unreadable files are skipped; the lab never needs them for its proof.
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
