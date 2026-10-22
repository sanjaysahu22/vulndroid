package com.vulndroid.app.core;

import android.app.Activity;

import com.vulndroid.app.R;
import com.vulndroid.app.labs.components.ComponentsLabModule;
import com.vulndroid.app.labs.deeplinks.DeepLinkLabModule;
import com.vulndroid.app.labs.network.NetworkLabModule;
import com.vulndroid.app.labs.secrets.SecretsLabModule;
import com.vulndroid.app.labs.storage.StorageLabModule;

public final class LabCatalog {

    public static final String STORAGE = "storage";
    public static final String SECRETS = "secrets";
    public static final String COMPONENTS = "components";
    public static final String DEEPLINKS = "deeplinks";
    public static final String NETWORK = "network";

    private static final LabInfo[] LABS = new LabInfo[]{
            new LabInfo(STORAGE, 1, "Insecure Data Storage",
                    "Storage", "Tokens and PII written in plaintext",
                    R.drawable.ic_lab_storage),
            new LabInfo(SECRETS, 2, "Hardcoded Secrets",
                    "Secrets", "Keys recoverable from the APK",
                    R.drawable.ic_lab_secrets),
            new LabInfo(COMPONENTS, 3, "Exported Components",
                    "Components", "Account data reachable by other apps",
                    R.drawable.ic_lab_components),
            new LabInfo(DEEPLINKS, 4, "Deep Link Hijacking",
                    "Deep links", "Unverified schemes and trusted parameters",
                    R.drawable.ic_lab_deeplinks),
            new LabInfo(NETWORK, 5, "Certificate Validation",
                    "TLS", "A trust-all client accepts any certificate",
                    R.drawable.ic_lab_network)
    };

    private LabCatalog() {
    }

    public static LabInfo[] all() {
        return LABS.clone();
    }

    public static LabInfo info(String id) {
        for (LabInfo lab : LABS) {
            if (lab.id.equals(id)) {
                return lab;
            }
        }
        return LABS[0];
    }

    public static LabModule module(Activity activity, String id) {
        switch (id) {
            case SECRETS:
                return new SecretsLabModule();
            case COMPONENTS:
                return new ComponentsLabModule();
            case DEEPLINKS:
                return new DeepLinkLabModule();
            case NETWORK:
                return new NetworkLabModule();
            case STORAGE:
            default:
                return new StorageLabModule();
        }
    }
}
