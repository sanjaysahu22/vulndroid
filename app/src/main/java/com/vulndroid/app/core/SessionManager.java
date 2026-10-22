package com.vulndroid.app.core;

/**
 * In-memory demo session. A real app would keep authorization state on the server and
 * use short-lived, revocable credentials; this is only enough to demonstrate local checks.
 */
public final class SessionManager {

    private static DemoAccounts.Account current;

    private SessionManager() {
    }

    public static synchronized void signIn(DemoAccounts.Account account) {
        current = account;
    }

    public static synchronized void signOut() {
        current = null;
    }

    public static synchronized boolean isSignedIn() {
        return current != null;
    }

    public static synchronized DemoAccounts.Account current() {
        return current;
    }
}
