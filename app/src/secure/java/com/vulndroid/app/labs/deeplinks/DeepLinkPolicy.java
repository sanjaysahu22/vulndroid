package com.vulndroid.app.labs.deeplinks;

import android.net.Uri;

import com.vulndroid.app.core.DemoAccounts;

import java.util.Set;

/**
 * Hardened URI policy: only HTTPS App Links for the exact host and /profile path are
 * considered. IDs are allowlisted, unknown parameters are rejected, sensitive actions are
 * never performed from a link, and profile data requires an authenticated session.
 */
public final class DeepLinkPolicy {

    private static final String HOST = "vulndroid.example";
    private static final String PATH = "/profile";

    private DeepLinkPolicy() {
    }

    public static DeepLinkResult handle(String url, boolean authenticated) {
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception e) {
            return DeepLinkResult.rejected("Rejected: unparseable URI",
                    "The link could not be parsed.", "Malformed input");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (!"https".equalsIgnoreCase(scheme)) {
            return DeepLinkResult.rejected("Rejected: wrong scheme",
                    "Only HTTPS App Links are accepted. Custom schemes can be claimed by any app.",
                    "scheme=" + scheme);
        }
        if (!HOST.equalsIgnoreCase(host)) {
            return DeepLinkResult.rejected("Rejected: wrong host",
                    "The host is not the verified application domain.",
                    "host=" + host + " expected=" + HOST);
        }
        if (!PATH.equals(path)) {
            return DeepLinkResult.rejected("Rejected: unsupported path",
                    "This build does not route arbitrary paths or admin actions from links.",
                    "path=" + path);
        }

        Set<String> params = uri.getQueryParameterNames();
        for (String name : params) {
            if (!"id".equals(name)) {
                return DeepLinkResult.rejected("Rejected: unexpected parameter",
                        "Only the documented id parameter is allowed.",
                        "unexpected=" + name);
            }
        }

        String rawId = uri.getQueryParameter("id");
        int id;
        try {
            id = Integer.parseInt(rawId == null ? "" : rawId);
        } catch (NumberFormatException e) {
            return DeepLinkResult.rejected("Rejected: invalid id",
                    "The id must be a numeric demo account identifier.",
                    "id=" + rawId);
        }
        if (id < 1 || id > 3) {
            return DeepLinkResult.rejected("Rejected: id outside allowlist",
                    "Only demo accounts 1–3 exist. Arbitrary identifiers are rejected.",
                    "id=" + id);
        }
        if (!authenticated) {
            return DeepLinkResult.rejected("Sign-in required",
                    "Opening a profile from a link requires an authenticated in-app session. "
                            + "A link alone is not proof of identity.",
                    "authenticated=false");
        }

        DemoAccounts.Account account = DemoAccounts.byId(id);
        if (account == null) {
            return DeepLinkResult.rejected("Rejected: unknown account",
                    "No matching demo account.", "id=" + id);
        }
        DeepLinkResult result = new DeepLinkResult();
        result.handled = true;
        result.approvedAction = false;
        result.title = "Profile link accepted";
        result.message = "The verified host, path, allowlisted id, and authenticated session all "
                + "passed. Only minimal profile data is shown; tokens are never placed in URLs.";
        result.exposedData = "Name: " + account.displayName + "\nRole: " + account.role
                + "\nEmail: " + account.email;
        result.checks = "https · host=vulndroid.example · path=/profile · id allowlist · "
                + "authenticated session · no privileged action";
        return result;
    }
}
