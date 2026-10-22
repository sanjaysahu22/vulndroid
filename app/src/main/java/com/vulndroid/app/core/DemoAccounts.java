package com.vulndroid.app.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake accounts used by every lab. Tokens are deliberately obvious dummies.
 * Nothing in this class is a real credential.
 */
public final class DemoAccounts {

    public static final class Account {
        public final int id;
        public final String username;
        public final String password;
        public final String displayName;
        public final String email;
        public final String role;
        public final String balance;
        public final String token;
        public final String note;

        Account(int id, String username, String password, String displayName, String email,
                String role, String balance, String token, String note) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.displayName = displayName;
            this.email = email;
            this.role = role;
            this.balance = balance;
            this.token = token;
            this.note = note;
        }

        public String summary() {
            return displayName + " · " + email + "\nRole: " + role + " · Demo balance: " + balance
                    + "\nToken: " + token;
        }
    }

    private static final List<Account> ACCOUNTS = new ArrayList<>();

    static {
        ACCOUNTS.add(new Account(1, "alice", "demo123", "Alice Morgan",
                "alice@example.test", "Customer", "$1,250.00",
                "DEMO_TOKEN_ALICE_8F3A91C2",
                "Primary demo account"));
        ACCOUNTS.add(new Account(2, "bob", "demo123", "Bob Chen",
                "bob@example.test", "Customer", "$540.25",
                "DEMO_TOKEN_BOB_4D7E22A1",
                "Secondary demo account"));
        ACCOUNTS.add(new Account(3, "carol", "demo123", "Carol Davis",
                "carol@example.test", "Support", "$0.00",
                "DEMO_TOKEN_CAROL_7C1B33E9",
                "Support demo account"));
    }

    private DemoAccounts() {
    }

    public static Account authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        for (Account account : ACCOUNTS) {
            if (account.username.equals(username.trim()) && account.password.equals(password)) {
                return account;
            }
        }
        return null;
    }

    public static Account byId(int id) {
        for (Account account : ACCOUNTS) {
            if (account.id == id) {
                return account;
            }
        }
        return null;
    }

    public static List<Account> all() {
        return new ArrayList<>(ACCOUNTS);
    }

    public static Account primary() {
        return ACCOUNTS.get(0);
    }
}
