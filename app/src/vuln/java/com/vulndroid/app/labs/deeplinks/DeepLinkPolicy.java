package com.vulndroid.app.labs.deeplinks;

import android.net.Uri;

import com.vulndroid.app.core.DemoAccounts;

/**
 * DELIBERATELY INSECURE deep-link handler.
 * It accepts an unverified custom scheme, trusts host/path/query parameters, exposes account
 * data by ID, and performs a privileged demo action without authentication.
 */
public final class DeepLinkPolicy {

    private DeepLinkPolicy() {
    }

    public static DeepLinkResult handle(String url, boolean authenticated) {
        DeepLinkResult result = new DeepLinkResult();
        result.handled = true;
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            result.title = "Unparseable link";
            result.message = e.getMessage();
            return result;
        }

        String host = uri.getHost() == null ? "" : uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath();

        if ("admin".equals(host) || path.contains("/approve")) {
            String txn = uri.getQueryParameter("txn");
            if (txn == null) {
                txn = "TXN-UNKNOWN";
            }
            result.approvedAction = true;
            result.title = "Refund approved";
            result.message = "Transaction " + txn + " was approved by a link. authenticated="
                    + authenticated + ", but this handler never checks it. A malicious app or "
                    + "web page can trigger the same action.";
            result.exposedData = "Action: approveRefund\nTransaction: " + txn
                    + "\nAuthorization check: none"
                    + "\nScheme: " + uri.getScheme()
                    + "\nHost: " + host + "  Path: " + path;
            result.checks = "No scheme allowlist, no host verification, no parameter validation, "
                    + "no authentication, no confirmation.";
            return result;
        }

        if ("profile".equals(host)) {
            String rawId = uri.getQueryParameter("id");
            int id = 1;
            try {
                id = Integer.parseInt(rawId == null ? "1" : rawId);
            } catch (NumberFormatException ignored) {
            }
            DemoAccounts.Account account = DemoAccounts.byId(id);
            if (account == null) {
                account = DemoAccounts.primary();
            }
            result.title = "Profile opened from link";
            result.message = "The handler trusts the caller-supplied id (requested="
                    + rawId + ") and returns demo data without checking who sent the link.";
            result.exposedData = account.summary() + "\nRequested id: " + rawId;
            result.checks = "Custom scheme, no App Links verification, no id allowlist, "
                    + "no session or caller authorization.";
            return result;
        }

        result.title = "Link accepted";
        result.message = "The handler accepted an unknown host/path instead of rejecting it.";
        result.exposedData = uri.toString();
        result.checks = "No allowlist for hosts or paths.";
        return result;
    }
}
