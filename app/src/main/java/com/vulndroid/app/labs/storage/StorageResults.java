package com.vulndroid.app.labs.storage;

import com.vulndroid.app.core.DemoAccounts;

import java.util.ArrayList;
import java.util.List;

public final class StorageResults {

    private StorageResults() {
    }

    public static final class LoginResult {
        public boolean success;
        public String message = "";
        public DemoAccounts.Account account;
        public String token = "";
        public String storageDetail = "";
        public boolean encrypted;
    }

    public static final class ScanResult {
        public boolean tokenFound;
        public final List<String> hits = new ArrayList<>();
        public boolean secureBlobPresent;
        public boolean decryptOk;
        public String cryptoDetail = "";
        public String summary = "";
    }
}
