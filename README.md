# VulnDroid

An offline Android security lab that ships the same app twice: once deliberately
vulnerable, once hardened. Every lab runs a **real check** against the installed APK
and reports what it actually observed — the status is never hard-coded.

| | |
|---|---|
| Vulnerable build | `com.vulndroid.app` |
| Secure build | `com.vulndroid.app.secure` |
| Companion attacker | `com.vulndroid.attacker` |
| Stack | Java + Android XML views, no dependencies, minSdk 24 |

## The labs

| # | Lab | What the vulnerable build does | What the secure build does |
|---|---|---|---|
| 01 | Insecure Data Storage | Writes the demo bearer token to SharedPreferences, SQLite and a private file | Stores only an AES‑GCM blob encrypted with a non-exportable Android Keystore key |
| 02 | Hardcoded Secrets | Embeds a fake API key as a DEX string constant | Ships only a public client id; the backend holds the private key |
| 03 | Exported Components | `ProfileActivity` is exported and returns account data to any caller | Component is not exported and requires an authenticated in-process session |
| 04 | Deep Link Hijacking | Accepts `vulndroid://` links and approves admin actions from them | Accepts only verified HTTPS App Links, allowlists parameters, requires a session |
| 05 | Certificate Validation | Trust-all `X509TrustManager` accepts a self-signed certificate | Platform trust store rejects the self-signed certificate |

Each lab screen has the same five sections: what the weakness is, how to test it, what
the secure implementation looks like, an interactive demo, and a **retest** button that
performs the check for real and stores the evidence.

## Build

```bash
./gradlew :app:assembleVulnDebug :app:assembleSecureDebug :attacker:assembleDebug
```

Output:

```
app/build/outputs/apk/vuln/debug/app-vuln-debug.apk
app/build/outputs/apk/secure/debug/app-secure-debug.apk
attacker/build/outputs/apk/debug/attacker-debug.apk
```

`tools/build_all.sh` wraps the same command and prints the install steps.

Requirements: JDK 17, Android SDK with platform 37 and build-tools 36, and an internet
connection for the first Gradle run.

## Install and run

Pre-built debug APKs are in [`docs/samples/`](docs/samples/README.md) if you do not want
to build first. Otherwise:

```bash
adb install -r app/build/outputs/apk/vuln/debug/app-vuln-debug.apk
adb install -r app/build/outputs/apk/secure/debug/app-secure-debug.apk
adb install -r attacker/build/outputs/apk/debug/attacker-debug.apk
```

Install the vulnerable and secure builds side by side — they have different package
names, so both can be present at once. Then:

1. Open **VulnDroid** and pick a lab.
2. Run the demo to see the behaviour.
3. Press **RUN VULNERABILITY TEST** (or **RUN SECURE RETEST** in the secure build).
4. Open **VulnDroid** → report to see the summary, the collected evidence and a
   shareable text report.

The debug builds are intentionally debuggable so that `adb run-as` can inspect the
app sandbox — that is the external half of the storage lab.

## Documentation

* [`docs/lab-manual.md`](docs/lab-manual.md) — step-by-step instructions for each lab
  plus the external (adb/JADX/Burp) verification commands.
* [`docs/architecture.md`](docs/architecture.md) — module layout, flavour source sets,
  status model, and how to add a new lab.
* [`docs/remediation-guide.md`](docs/remediation-guide.md) — the fix for each
  weakness, with the code that implements it.
* [`tools/https_test_server.py`](tools/https_test_server.py) — desktop self-signed
  HTTPS endpoint used for TLS experiments from a workstation.

## Safety

Everything in this project is fake: accounts, passwords, bearer tokens, the API key and
the TLS certificate. The builds are training artifacts, not production software, and
the vulnerable flavour is unsafe to ship. Use them only on a test device.

```bash
adb uninstall com.vulndroid.app
adb uninstall com.vulndroid.app.secure
adb uninstall com.vulndroid.attacker
```

## License

Provided as-is for security education and internal training.
