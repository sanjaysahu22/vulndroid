# VulnDroid lab manual

Every lab below has three parts:

* **Demo** — an in-app screen that shows the behaviour with one tap.
* **Retest** — a real check that runs against the installed APK and produces evidence.
  The status chip only changes because of this check.
* **External check** — the same result reproduced from outside the app with `adb`,
  JADX, `strings` or a proxy, so the finding is not just "the app says so".

Run the manual against the vulnerable build first, then against the secure build.
Statuses are stored per installation, so the two APKs keep separate results.

Demo accounts (all fake): `alice / demo123`, `bob / demo123`, `carol / demo123`.

---

## Lab 01 — Insecure Data Storage

**Weakness.** The vulnerable build persists a replayable bearer token as plaintext in
three ordinary places inside the app sandbox: `shared_prefs/user_data.xml`,
`databases/vulndroid.db` (table `sessions`) and `files/session.txt`. It also writes the
token to logcat. App-private storage is only private while the sandbox holds.

**Demo.** Lab 01 → *SIGN IN & WRITE SESSION* → *INSPECT APP-PRIVATE STORAGE*.

**Retest.** Press **RUN VULNERABILITY TEST**. The scanner walks the data directory and
searches for the demo token.

* Vulnerable build → `VULNERABLE`, evidence lists the exact files.
* Secure build → `FIXED`. Only an AES-GCM blob encrypted with a non-exportable
  Android Keystore key is persisted; the retest decrypts it again to prove the
  session still works.

**External check** (debug build only):

```bash
adb shell run-as com.vulndroid.app ls -R .
adb shell run-as com.vulndroid.app cat shared_prefs/user_data.xml
adb shell run-as com.vulndroid.app cat files/session.txt
adb exec-out run-as com.vulndroid.app cat databases/vulndroid.db > vulndroid.db
```

In MobSF, use the dynamic-analysis file explorer and open the same three paths.

**Note.** `run-as` only works while the APK is debuggable. On a production build you
need root or another way into the sandbox — the insecure storage is still the root
cause whenever that access happens.

---

## Lab 02 — Hardcoded Secrets

**Weakness.** The vulnerable build contains a fake API key as a static field. DEX string
constants are trivial to locate with JADX, `strings` or MobSF. Anything shipped in the
APK is public, including keys that "look" private.

**Demo.** Lab 02 → *EXTRACT SECRET FROM THIS APK* → *SIMULATE AUTHENTICATED API CALL*.

**Retest.** The in-app scanner walks the same ZIP entries a reverse engineer would
inspect and reports the entry name, the offset and the number of occurrences.

* Vulnerable build → `VULNERABLE`.
* Secure build → `FIXED`. Only the public client id `vulndroid-public-client` remains.

**External check:**

```bash
jadx -d jadx-vuln app/build/outputs/apk/vuln/debug/app-vuln-debug.apk
grep -R "DEMO_KEY" jadx-vuln

unzip -p app/build/outputs/apk/vuln/debug/app-vuln-debug.apk '*.dex' | strings | grep DEMO_KEY
```

The APK is split across several DEX files, so scan `*.dex` rather than a single
`classes.dex` — looking at one file is a classic way to miss a secret.

In MobSF, load the APK and read the hardcoded-secrets findings.

---

## Lab 03 — Exported Components

**Weakness.** The vulnerable manifest declares `ProfileActivity` with
`android:exported="true"` and no permission. The activity trusts a caller-supplied
`user_id`, performs no session check, and returns email, balance and the demo bearer
token to whoever launched it for a result. Any installed app that knows the component
name can read the data.

**Demo.** Lab 03 → *OPEN PROFILE IN THIS APP* (local, for comparison) and
*OPEN VULNDROID ATTACKER* → select the target package → *LAUNCH PROFILE ACTIVITY*.

**Retest.** The retest reads the effective `ActivityInfo` from `PackageManager` instead
of trusting the screen's own description.

* Vulnerable build → `VULNERABLE`.
* Secure build → `FIXED` (`android:exported="false"`, plus a session check inside the
  activity).

**External check:**

```bash
adb shell am start -n com.vulndroid.app/.labs.components.ProfileActivity --ei user_id 2
adb shell am start -n com.vulndroid.app.secure/.labs.components.ProfileActivity --ei user_id 2

aapt2 dump xmltree app/build/outputs/apk/vuln/debug/app-vuln-debug.apk \
  --file AndroidManifest.xml | grep -A 8 ProfileActivity

adb shell dumpsys package com.vulndroid.app | grep -A 8 ProfileActivity
```

The vulnerable build opens a profile and hands the data back to the caller; the secure
build raises `SecurityException`.

**Note.** An exported activity is not automatically a vulnerability. The problem is
exported access combined with sensitive data and no authorization check.

---

## Lab 04 — Deep Link Hijacking

**Weakness.** The vulnerable build registers an unverified custom scheme
(`vulndroid://`) and exports a handler that accepts any host/path shape, opens any demo
profile by caller-supplied `id`, and approves a demo refund from
`vulndroid://admin/approve?txn=…` without authentication or confirmation. The
companion attacker app claims the same scheme, so a link meant for the victim can be
delivered to the attacker instead.

**Demo.** Lab 04 → paste a URI → tick *Simulate an authenticated in-app session* if you
want to compare → *SIMULATE INCOMING LINK*.

**Retest.** The retest sends the admin link to the real handler with no session and
counts the app's own handlers for the `vulndroid://` scheme.

* Vulnerable build → `VULNERABLE`, the refund is approved.
* Secure build → `FIXED`, the policy rejects the link and no custom scheme is registered.

**External check:**

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "vulndroid://admin/approve?txn=TXN-1042"

adb shell am start -a android.intent.action.VIEW \
  -d "vulndroid://profile?id=2"
```

Install the attacker app and send the same link again — both apps register the scheme,
so the chooser appears. Set the attacker as the default handler in system settings for
fully automatic hijacking, then use *FORWARD* to relay a crafted link to the victim.

The secure build only routes `https://vulndroid.example/profile`. Real App Link
verification also needs `https://vulndroid.example/.well-known/assetlinks.json` with
the APK signing certificate's SHA-256 fingerprint. The example domain is not registered
for this lab, so exercise the secure handler locally with an explicit component:

```bash
adb shell am start -n com.vulndroid.app.secure/.labs.deeplinks.DeepLinkActivity \
  -a android.intent.action.VIEW -d "https://vulndroid.example/profile?id=2"
```

---

## Lab 05 — Certificate Validation

**Weakness.** The vulnerable build installs a trust-all `X509TrustManager` and a
hostname verifier that returns `true` for every host, so any intercepting proxy can
terminate TLS with its own certificate and read or modify traffic.

**Demo.** Lab 05 → *SEND HTTPS REQUEST*. The lab starts a loopback HTTPS server with a
self-signed certificate, so no network access is needed.

**Retest.** A real handshake is performed against that endpoint.

* Vulnerable build → `VULNERABLE`, the request completes with HTTP 200.
* Secure build → `FIXED`, the handshake is refused because the certificate is not in
  the platform trust store.

**External check** with an intercepting proxy:

```bash
adb shell settings put global http_proxy 10.0.2.2:8080
# browse or run the request, then remove the proxy:
adb shell settings put global http_proxy :0
```

Burp must listen on all interfaces. The vulnerable build accepts Burp's certificate
without installing a CA; the secure build rejects the untrusted chain (targetSdk 34
does not trust user-installed CAs by default). A debug-only network security config
can opt into a user CA for testing — do not ship that override.

Inspect the certificate independently:

```bash
openssl s_client -connect 127.0.0.1:8443 -servername localhost
```

`tools/https_test_server.py` provides the same endpoint on a workstation.

---

## Reading the report

Open the app → **REPORT** to see:

* the summary of the five labs for this installation,
* the exact evidence collected by each retest,
* the remediation notes for every lab,
* a shareable plain-text report.

**Clear test results** resets the statuses to *NOT TESTED*. Demo data is left alone.

A green result covers only the specific check that ran in this build. It is not a
statement that the whole app is secure.
