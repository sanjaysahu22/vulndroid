package com.vulndroid.app.core;

import android.app.Activity;
import android.view.View;

public interface LabModule {

    String intro();

    String vulnerability();

    String testing();

    String secureImplementation();

    String summary();

    int demoLayoutRes();

    /** Wire up the interactive demo. Buttons may use background threads for network work. */
    void bindDemo(View demo, Activity activity);

    /**
     * Runs a real check and returns evidence. Called on a background thread, so blocking
     * operations such as file scans and TLS requests are expected here.
     */
    LabTestResult runTest(Activity activity) throws Exception;

    default void onDestroy() {
    }
}
