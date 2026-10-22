package com.vulndroid.app.core;

/**
 * Builds the demo key at runtime from individual chars. That keeps the full literal out of
 * the secure APK, so the secure build's self-scan is meaningful rather than finding its own
 * scanner needle.
 */
public final class SecretNeedle {

    private SecretNeedle() {
    }

    public static String demoApiKey() {
        return new String(new char[]{
                'D', 'E', 'M', 'O', '_', 'K', 'E', 'Y', '_', 'N', 'O', 'T', '_',
                'V', 'A', 'L', 'I', 'D', '_', '1', '2', '3', '4', '5'
        });
    }
}
