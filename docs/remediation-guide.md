# Remediation guide

One section per lab: the weakness, the fix with the code that implements it, how to
verify it, and the mistakes that are easy to make.

The fixes below are the ones the **secure** flavour actually ships, so you can read
them next to the vulnerable code in `app/src/vuln/`.

---

## 1. Insecure data storage

**Weakness.** A reusable bearer token and account PII written as plaintext into
SharedPreferences, a SQLite database and a private file.

**Fix.** Persist nothing that does not need to be persisted, and if something must be
persisted, encrypt it with a key that cannot leave the device.

```java
KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build();

KeyGenerator generator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
generator.init(spec);
SecretKey key = generator.generateKey();

Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
cipher.init(Cipher.ENCRYPT_MODE, key);
byte[] iv = cipher.getIV();
byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
String blob = Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
        + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
```

The key is generated inside the Android Keystore and is **non-exportable**: the app can
ask the Keystore to encrypt and decrypt, but it can never read the key material, so a
stolen ciphertext file is useless on another device.

The rest of the fix is about how much you keep:

* keep email and balance in memory, or fetch them on demand;
* give the session a short TTL and expire it in code;
* revoke the session server-side so a leaked blob stops working;
* delete legacy artifacts when the user signs in, so an upgrade does not leave the old
  plaintext files behind;
* never log tokens. `Log.w("VulnDroidLab", "… token=" + token)` is a leak even when
  logcat is restricted — anyone with a device or a bug report can read it.

**Verify.** Run the storage retest: the secure build reports `FIXED` only when no
plaintext token is found on disk *and* the encrypted blob still decrypts.

**Common mistakes**

* Encrypting with a hardcoded key in the APK — that is the secrets lab again.
* Using `MODE_WORLD_READABLE` / `MODE_WORLD_WRITEABLE` on shared preferences.
* Storing the token in a `WebView` cache, a `SQLite` WAL, or a `SharedPreferences`
  backup file that nobody remembered to exclude.
* Assuming "app-private" means "unreachable". `run-as` on a debuggable build, root, a
  backup extraction and malware with device access all break that boundary.

---

## 2. Hardcoded secrets

**Weakness.** A private API key compiled into the DEX as a string constant.

**Fix.** Move the credential to a backend and keep only a public identifier in the app.

```
client (public client id)  ──▶  backend (private key, authorization)
                                        │
                                        └──▶ short-lived, narrowly scoped token
```

The app authenticates to *your* API, the backend checks the caller and the
authorization, and it either performs the privileged call itself or returns a
short-lived token with a small scope.

Operational requirements that matter as much as the code:

* rotate every key that has ever shipped inside an APK;
* scope keys per service and per environment;
* monitor usage so abuse of a leaked key is noticed;
* keep public identifiers in the APK — an OAuth client id is not a secret, provided
  the server enforces redirect URIs, scopes and caller authorization.

**Verify.** The secure build's self-scan finds no private key. Confirm from outside:

```bash
unzip -p app/build/outputs/apk/secure/debug/app-secure-debug.apk '*.dex' | strings | grep DEMO_KEY
```

Scan every DEX file, not just `classes.dex` — the build is split across several.

**Common mistakes**

* Obfuscation. R8/ProGuard raises the effort slightly; it does not make a value secret.
* "The key only works for our demo account." It still ships to every user.
* Moving the secret to `BuildConfig` or a `resValue` — both end up in the APK.
* Mobile attestation as a secret store. Attestation answers "which app is this?", not
  "who is allowed to call the API?".

---

## 3. Exported components

**Weakness.** `ProfileActivity` is exported with no permission, trusts a
caller-supplied `user_id`, and returns account data — including the demo bearer token —
in the activity result.

**Fix (primary).** Do not export it.

```xml
<activity
    android:name=".labs.components.ProfileActivity"
    android:exported="false" />
```

The OS enforces this before your code runs, which makes it the strongest control
available.

**Fix (when external access is genuinely required).** Then do all of the following:

```xml
<permission
    android:name="com.vulndroid.app.permission.READ_PROFILE"
    android:protectionLevel="signature" />

<activity
    android:name=".labs.components.ProfileActivity"
    android:exported="true"
    android:permission="com.vulndroid.app.permission.READ_PROFILE" />
```

* validate every extra, URI and action, and reject unexpected input;
* authenticate and authorize the caller before returning anything;
* return the minimum data, and never return bearer tokens to another app;
* ignore caller-supplied identity — read the authenticated session instead.

The secure flavour does exactly this: `exported="false"` in the manifest, and the
activity reads `SessionManager.current()` rather than the `user_id` extra.

**Verify.** The retest reads the effective `ActivityInfo` from `PackageManager`, so it
reflects what is really installed:

```bash
adb shell dumpsys package com.vulndroid.app | grep -A 8 ProfileActivity
```

**Common mistakes**

* Treating `exported="true"` as harmless because "the activity does nothing on its
  own". It does something when an attacker supplies the extras.
* Relying on a secret component name or an obfuscated class name.
* Forgetting that `intent-filter` implies exported on older target SDKs.
* Returning a refresh token through `setResult()`.

---

## 4. Deep link hijacking

**Weakness.** An unverified custom scheme (`vulndroid://`) with a handler that trusts
host, path and query parameters, exposes accounts by `id`, and approves a privileged
action from the link alone.

**Fix (transport).** Use verified Android App Links over HTTPS:

```xml
<activity
    android:name=".labs.deeplinks.DeepLinkActivity"
    android:autoVerify="true"
    android:exported="true">
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="https"
            android:host="vulndroid.example"
            android:pathPrefix="/profile" />
    </intent-filter>
</activity>
```

Verification requires `https://vulndroid.example/.well-known/assetlinks.json` listing
the APK signing certificate's SHA-256 fingerprint. A custom scheme cannot be protected
this way — any app can claim it.

**Fix (validation).** Verification is configuration, and configuration can fail, so the
handler validates too:

```java
if (!"https".equalsIgnoreCase(scheme))      return reject("wrong scheme");
if (!HOST.equalsIgnoreCase(host))            return reject("wrong host");
if (!PATH.equals(path))                      return reject("unsupported path");
for (String name : uri.getQueryParameterNames()) {
    if (!"id".equals(name))                   return reject("unexpected parameter");
}
int id = parseId(uri.getQueryParameter("id"));
if (id < 1 || id > 3)                        return reject("id outside allowlist");
if (!authenticated)                          return reject("sign-in required");
```

**Fix (authorisation).** A link is not proof of identity:

* sensitive actions require an authenticated session and a server-side authorization
  decision — never a link alone;
* never trigger a destructive or financial action without confirmation;
* never put tokens, passwords or other secrets in a URL — URLs end up in logs, in
  `Referer` headers and in the user's history.

**Verify.** The retest sends the admin link to the real handler with no session. The
secure build also registers no handler for the `vulndroid://` scheme, which the
retest verifies by querying `PackageManager`.

**Common mistakes**

* Assuming `autoVerify="true"` alone is enough — the handler still has to check the
  host, the path and every parameter.
* Registering a custom scheme "temporarily" and shipping it.
* Trusting `intent.getData()` because it "came from the app".
* Accepting an `id` range from the link and querying the account store with it.

---

## 5. Certificate validation

**Weakness.** A trust-all `X509TrustManager` plus a hostname verifier that returns
`true` for every host. TLS still encrypts the bytes, but the client has no idea who it
is talking to, so any proxy can terminate the connection.

**Fix.** Use the platform's standard validation and change nothing:

```java
// No custom SSLContext, no custom HostnameVerifier.
// HttpsURLConnection validates the chain and the hostname against the
// platform trust anchors.
HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
connection.setConnectTimeout(6000);
connection.setReadTimeout(6000);
```

With OkHttp, use the default `OkHttpClient`; if you must add a custom trust manager,
it must chain to the platform trust anchors and still verify the hostname:

```java
X509TrustManager trustManager = (chain, authType) -> {
    // Delegate to the platform default; never return without checking.
    platformTrustManager.checkServerTrusted(chain, authType);
};
```

If you need a private CA, scope it with a network security configuration instead of
a trust manager:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">partner.example</domain>
        <trust-anchors>
            <certificates src="@raw/partner_ca" />
        </trust-anchors>
    </domain-config>
</network-security-config>
```

**Verify.** The retest performs a real handshake against a self-signed loopback
endpoint. The secure build fails the handshake, and that failure is the proof.

**Common mistakes**

* Trust-all in "debug only" code that reaches a release build. Use `BuildConfig`
  guards *and* a release-time network security configuration; assume it will leak.
* A trust manager that only checks the expiry date, or that catches the validation
  exception and continues.
* `HostnameVerifier` that returns `true`.
* Pinning without a rotation plan — pinning without rotation can brick your clients
  the day the certificate is replaced.
* Pinning as a *substitute* for chain and hostname validation rather than an
  addition to it.
* `usesCleartextTraffic="true"` "just for the local lab" — the setting applies to the
  whole app.
