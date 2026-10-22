package com.vulndroid.app.labs.network;

public final class TlsFetchResult {
    public boolean success;
    public int statusCode;
    public String body = "";
    public String errorType = "";
    public String errorMessage = "";
    public boolean certificateRejected;
    public String policy = "";
    public String cipherSuite = "";
    public String peer = "";
}
