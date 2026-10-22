package com.vulndroid.app.labs.storage;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.vulndroid.app.R;
import com.vulndroid.app.core.LabTestResult;
import com.vulndroid.app.core.Ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A convenience view over the app's own sandbox. It mirrors what `adb run-as` or a rooted
 * shell would see; it is not itself the vulnerability.
 */
public class StorageInspectorActivity extends Activity {

    private LinearLayout content;
    private File dataDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage_inspector);
        content = findViewById(R.id.inspectorContent);
        dataDir = getDataDir();
        findViewById(R.id.inspectorBack).setOnClickListener(v -> finish());
        render();
    }

    private void render() {
        StorageResults.ScanResult scan = StorageLabOps.scan(this);

        LabTestResult.Status status = scan.tokenFound
                ? LabTestResult.Status.VULNERABLE
                : (scan.secureBlobPresent && scan.decryptOk
                        ? LabTestResult.Status.FIXED : LabTestResult.Status.INCONCLUSIVE);

        LinearLayout summary = Ui.card(this);
        LinearLayout header = Ui.row(this);
        TextView label = Ui.sectionLabel(this, "SANDBOX SCAN");
        header.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(Ui.statusChip(this, status));
        summary.addView(header);
        TextView summaryText = Ui.body(this, scan.summary + "\n\n" + scan.cryptoDetail);
        summaryText.setPadding(0, Ui.dp(this, 10), 0, 0);
        summary.addView(summaryText);
        addCard(summary);

        LinearLayout warning = Ui.card(this, R.drawable.bg_card_soft);
        TextView warn = Ui.body(this,
                "This screen reads the app's own private directory. It is here so the exposure is "
                        + "visible without root. On a debug build, adb run-as gives an external "
                        + "attacker exactly the same view.");
        warning.addView(warn);
        addCard(warning);

        addHeading("SHARED PREFERENCES");
        dumpPrefs();

        addHeading("SQLITE DATABASE");
        dumpDatabase();

        addHeading("PRIVATE FILE");
        dumpSessionFile();

        addHeading("ADB EQUIVALENT");
        String pkg = getPackageName();
        addCode("adb shell run-as " + pkg + " ls -R .\n"
                + "adb shell run-as " + pkg + " cat shared_prefs/user_data.xml\n"
                + "adb shell run-as " + pkg + " cat files/session.txt\n"
                + "adb exec-out run-as " + pkg + " cat databases/vulndroid.db > vulndroid.db");

        TextView foot = Ui.body(this,
                "The parseable proof is the demo token. Copy it from a file and compare it with the "
                        + "token shown in the lab after sign-in. Same value means the sandbox leaked a "
                        + "replayable credential.");
        foot.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 24));
        content.addView(foot);
    }

    private void dumpPrefs() {
        File dir = new File(dataDir, "shared_prefs");
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            addCode("shared_prefs/ is empty or absent.");
            return;
        }
        for (File file : files) {
            String body = readText(file);
            addCode("shared_prefs/" + file.getName() + "\n\n" + body);
        }
    }

    private void dumpDatabase() {
        File dbFile = getDatabasePath("vulndroid.db");
        if (!dbFile.exists()) {
            addCode("databases/vulndroid.db is absent. The secure build removes the legacy database.");
            return;
        }
        StringBuilder out = new StringBuilder();
        out.append("databases/vulndroid.db (").append(dbFile.length()).append(" bytes)\n\n");
        try (SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile.getPath(), null,
                SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor cursor = db.rawQuery("SELECT username, token, email FROM sessions", null)) {
                while (cursor.moveToNext()) {
                    out.append("username=").append(cursor.getString(0)).append("\n")
                            .append("token=").append(cursor.getString(1)).append("\n")
                            .append("email=").append(cursor.getString(2)).append("\n");
                }
            }
        } catch (Exception e) {
            out.append("Could not query table: ").append(e.getMessage());
        }
        addCode(out.toString());
    }

    private void dumpSessionFile() {
        File file = new File(getFilesDir(), "session.txt");
        if (!file.exists()) {
            addCode("files/session.txt is absent. The secure build never creates it.");
            return;
        }
        addCode("files/session.txt\n\n" + readText(file));
    }

    private String readText(File file) {
        if (file.length() > 64 * 1024) {
            return "(file too large to display in this demo)";
        }
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (text.length() > 4000) {
                return text.substring(0, 4000) + "\n… (truncated)";
            }
            return text;
        } catch (IOException e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }

    private void addHeading(String text) {
        TextView label = Ui.sectionLabel(this, text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 14);
        params.bottomMargin = Ui.dp(this, 8);
        content.addView(label, params);
    }

    private void addCode(String text) {
        TextView code = Ui.codeBlock(this, text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 10);
        content.addView(code, params);
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(this, 14);
        content.addView(card, params);
    }
}
