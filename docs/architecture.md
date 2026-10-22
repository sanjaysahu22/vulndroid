# Architecture

## Module map

```
VulnDroid
├── app/                     the lab app, built in two flavours
│   ├── src/main/            shared code, resources and manifest
│   ├── src/vuln/            deliberately insecure implementations
│   └── src/secure/          hardened implementations
├── attacker/                companion app that attacks the vulnerable build
├── docs/                    this documentation
└── tools/                   desktop helpers (self-signed HTTPS server)
```

`settings.gradle` includes two Gradle modules: `:app` and `:attacker`. The root
`build.gradle` declares the Android Gradle Plugin version; each module has no
third-party dependencies, which keeps the APKs small and the lab self-contained.

## Flavour model

`:app` has one flavour dimension, `mode`, with two product flavours:

| Flavour | Application id | `BuildConfig.VULNERABLE_BUILD` |
|---|---|---|
| `vuln` | `com.vulndroid.app` | `true` |
| `secure` | `com.vulndroid.app.secure` | `false` |

The two flavours have **different application ids**, so both can be installed on one
device at the same time and compared side by side.

Four classes exist in both flavours with the same fully qualified name and different
bodies. The flavour source set decides which one is compiled in:

| Class | `vuln` | `secure` |
|---|---|---|
| `labs.secrets.SecretSource` | returns a hardcoded fake API key | returns `null`; exposes a public client id only |
| `labs.storage.StorageLabOps` | writes plaintext prefs/SQLite/file | writes a Keystore-encrypted blob |
| `labs.network.TlsConfigProvider` | trust-all `X509TrustManager`, permissive hostname verifier | no-op; the platform policy applies |
| `labs.deeplinks.DeepLinkPolicy` | accepts any custom-scheme link, approves admin actions | allowlists scheme, host, path and parameters |

The flavour manifests (`app/src/vuln/AndroidManifest.xml`, `app/src/secure/AndroidManifest.xml`)
overlay the main manifest and differ in exactly two places: the `exported` flag of
`ProfileActivity` and the intent filter of `DeepLinkActivity`.

This is the central design decision of the project: the vulnerable code is *absent*
from the secure APK rather than merely disabled at runtime, so a scan of the secure
build finds nothing.

## Shared code

Everything under `app/src/main/java/com/vulndroid/app/core/` is flavour-agnostic.

| Class | Responsibility |
|---|---|
| `LabModule` | the contract every lab implements |
| `LabCatalog` | the list of labs, and the factory that maps an id to a module |
| `LabInfo` | immutable metadata for one lab (number, title, tagline, icon) |
| `LabTestResult` | status enum, headline, evidence and timestamp of one test |
| `LabStatusStore` | persists test results in SharedPreferences `lab_results` |
| `Ui` | small view-construction helpers (cards, chips, code blocks) |
| `DemoAccounts` | the fake accounts, tokens and PII used by every lab |
| `SessionManager` | in-memory session used by the components and deep-link labs |
| `ApkScanner` | dependency-free ZIP/DEX string scanner used by the secrets lab |
| `SecretNeedle` | builds the demo key from individual chars so the *needle* for the self-scan is not itself a literal in the secure DEX |

## The lab contract

```java
public interface LabModule {
    String intro();                 // what the weakness is
    String vulnerability();         // how the vulnerable build is broken
    String testing();               // how to reproduce it, including external commands
    String secureImplementation();  // the fix
    String summary();               // the takeaway
    int demoLayoutRes();            // the interactive demo layout
    void bindDemo(View demo, Activity activity);
    LabTestResult runTest(Activity activity) throws Exception;   // background thread
    default void onDestroy() {}
}
```

`runTest` is the only method that decides a status. It runs on a background thread
(`LabActivity`), may block (filesystem walks, APK scans, TLS handshakes) and returns a
`LabTestResult` with a status, a one-line headline and the evidence that produced it.

`LabTestResult.Status` has four values:

* `NOT_TESTED` — no test has run in this installation.
* `VULNERABLE` — the test observed the weakness.
* `FIXED` — the test observed the control working.
* `INCONCLUSIVE` — the test could not establish either (for example the local HTTPS
  server refused to start). This is deliberately distinct from `FIXED`: a lab never
  reports success it did not actually measure.

## Screen flow

```
MainActivity            list of labs + per-lab status chips + progress counter
  └── LabActivity       one lab: prose sections, interactive demo, retest button
        ├── StorageInspectorActivity
        ├── ProfileActivity          (also launched by the attacker app)
        └── DeepLinkActivity         (also reachable by external intents)

ReportActivity          summary, evidence and remediation for this installation
AboutActivity           what the app is, how status works, how to clean up
```

`LabActivity.runTest()` runs the module's test on a named background thread, saves the
result with `LabStatusStore`, and re-renders the status chip and evidence block.
`ReportActivity` reads the same store, so the report only ever contains results that
were actually produced on the device.

## Storage of results

`LabStatusStore` writes to the SharedPreferences file `lab_results` with the keys
`status_<labId>`, `headline_<labId>`, `evidence_<labId>` and `time_<labId>`. Nothing
leaves the device: the report is generated on-device and only shared if the user
explicitly shares it.

The two flavours use different application ids, therefore different sandboxes, so their
results are independent — as the app states on screen.

## The companion attacker

`:attacker` is a single-activity app whose only purpose is to exercise the two
exported-surface labs from outside the app boundary:

* **LAUNCH PROFILE ACTIVITY** — `startActivityForResult` against
  `com.vulndroid.app[.secure]/.labs.components.ProfileActivity` with `--ei user_id 2`.
  On the vulnerable build the profile comes back in the activity result; on the secure
  build the call raises `SecurityException`.
* **SEND LINK** / **FORWARD LINK** — registers the `vulndroid://` scheme and captures
  the URI it receives, then relays it to the victim's handler.

It is a teaching tool, not an exploit framework, and it only targets the VulnDroid
packages.

## Resources

* Colours live in `app/src/main/res/values/colors.xml` with the `vd_` prefix
  (`vd_primary`, `vd_red`, `vd_green`, `vd_amber`, `vd_text`, `vd_text_muted`,
  `vd_text_faint`, `vd_border_soft`, `vd_red_bg`, `vd_green_bg`).
* Reusable widget styles live in `themes.xml` (`VdSectionLabel`, `VdInput`,
  `VdButton`, `VdButtonSecondary`).
* Backgrounds and icons are vector drawables named `bg_*` and `ic_*`; the lab icons are
  `ic_lab_storage`, `ic_lab_secrets`, `ic_lab_components`, `ic_lab_deeplinks`,
  `ic_lab_network`.

There is no `androidx` dependency: the app uses the platform `Activity`, `AlertDialog`
and `LinearLayout` APIs directly, which keeps the build simple and the behaviour easy
to trace.

## Adding a new lab

1. Add a `LabModule` implementation in `app/src/main/java/com/vulndroid/app/labs/<name>/`.
2. Add the flavour-specific behaviour as a class with the same FQCN in
   `app/src/vuln/java/…` and `app/src/secure/java/…`.
3. Add a demo layout `demo_<name>.xml` and any activity the lab needs.
4. Register the lab in `LabCatalog`: a constant, a `LabInfo` entry with a unique
   `number`, and a `case` in `LabCatalog.module()`.
5. Add the `ic_lab_<name>` icon and a `bg_*` background if needed.

`MainActivity` and `ReportActivity` derive their counts from `LabCatalog.all()`, so the
progress counter and the report totals follow the catalogue automatically.
