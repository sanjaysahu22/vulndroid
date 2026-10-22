# Sample APKs

Pre-built debug APKs of the three modules, so you can install the lab without
building it first.

| File | Package | What it is |
|---|---|---|
| `app-vuln-debug.apk` | `com.vulndroid.app` | the vulnerable training build |
| `app-secure-debug.apk` | `com.vulndroid.app.secure` | the hardened reference build |
| `attacker-debug.apk` | `com.vulndroid.attacker` | the companion attacker app |

Install all three on a test device or emulator:

```bash
adb install -r app-vuln-debug.apk
adb install -r app-secure-debug.apk
adb install -r attacker-debug.apk
```

The two app builds have different package names, so they live side by side. The
vulnerable build is debuggable on purpose — `adb run-as` is part of the storage lab.

These are debug builds: they are signed with the standard debug key, they are not
minified, and they must never be deployed anywhere real. Everything they contain —
accounts, tokens, the API key, the TLS certificate — is fake.

## Rebuilding

The APKs in this folder are rebuilt from source with:

```bash
./tools/build_all.sh
```

The script prints the same install commands. Copy the fresh output over these files
if you want the samples to match your local build.
