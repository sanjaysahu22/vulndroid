#!/usr/bin/env python3
"""Desktop HTTPS server for the VulnDroid certificate-validation lab.

The Android app starts its own loopback HTTPS server (LocalHttpsServer) with a
self-signed certificate, so the TLS lab works with no network access. This script
is the desktop counterpart: it serves the same endpoint with the same kind of
untrusted certificate, so you can point a proxy, a mobile device, or a command
line tool at it.

It is a teaching tool. The certificate it creates is self-signed and is trusted by
nobody, which is exactly the point: a client that validates the chain must refuse
to talk to it, and a client with a trust-all TLS policy will happily proceed.

Usage:
    python3 tools/https_test_server.py                 # https://127.0.0.1:8443
    python3 tools/https_test_server.py --port 9443
    python3 tools/https_test_server.py --cert cert.pem --key key.pem
    python3 tools/https_test_server.py --generate-only

Then, from the same machine:
    curl -k https://127.0.0.1:8443/api/account
    openssl s_client -connect 127.0.0.1:8443 -servername localhost

From an Android device on the same network (use the host's LAN address):
    python3 tools/https_test_server.py --host 0.0.0.0 --port 8443
    adb reverse tcp:8443 tcp:8443
"""

from __future__ import annotations

import argparse
import http.server
import json
import os
import ssl
import subprocess
import sys
import threading
from datetime import datetime

HOST = "127.0.0.1"
PORT = 8443
CERT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "certs")
CERT_FILE = os.path.join(CERT_DIR, "lab_tls.crt")
KEY_FILE = os.path.join(CERT_DIR, "lab_tls.key")

RESPONSE_BODY = {
    "account": "alice",
    "balance": "$1,250.00",
    "message": "Served over a self-signed certificate",
}


def log(message: str) -> None:
    stamp = datetime.now().strftime("%H:%M:%S")
    print(f"[{stamp}] {message}", flush=True)


def generate_self_signed(cert_file: str, key_file: str, host: str) -> None:
    """Create a self-signed certificate for `host` using the openssl CLI."""
    os.makedirs(os.path.dirname(cert_file), exist_ok=True)
    san = f"DNS:localhost,IP:{host}" if host not in ("0.0.0.0", "::") else "DNS:localhost,IP:127.0.0.1"
    command = [
        "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
        "-keyout", key_file, "-out", cert_file,
        "-days", "365", "-subj", "/CN=vulndroid-lab",
        "-addext", f"subjectAltName={san}",
    ]
    log(f"generating self-signed certificate for {host} …")
    try:
        subprocess.run(command, check=True, capture_output=True)
    except FileNotFoundError:
        raise SystemExit(
            "openssl was not found on PATH. Install it, or pass --cert/--key "
            "with your own certificate."
        )
    except subprocess.CalledProcessError as error:
        detail = error.stderr.decode("utf-8", "replace").strip()
        raise SystemExit(f"openssl failed:\n{detail}")
    log(f"certificate written to {cert_file}")


class LabHandler(http.server.BaseHTTPRequestHandler):
    server_version = "VulnDroidLab/1.0"
    protocol_version = "HTTP/1.1"

    def _respond(self) -> None:
        payload = json.dumps(RESPONSE_BODY).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self) -> None:  # noqa: N802 - required by BaseHTTPRequestHandler
        log(f"GET {self.path}  (client {self.client_address[0]})")
        self._respond()

    def do_POST(self) -> None:  # noqa: N802 - required by BaseHTTPRequestHandler
        length = int(self.headers.get("Content-Length") or 0)
        if length:
            self.rfile.read(length)
        log(f"POST {self.path}  (client {self.client_address[0]})")
        self._respond()

    def log_message(self, fmt: str, *args) -> None:  # silence the default logger
        return


def serve(host: str, port: int, cert_file: str, key_file: str) -> None:
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(certfile=cert_file, keyfile=key_file)

    httpd = http.server.ThreadingHTTPServer((host, port), LabHandler)
    httpd.socket = context.wrap_socket(httpd.socket, server_side=True)

    shown = "127.0.0.1" if host in ("0.0.0.0", "") else host
    log(f"serving https://{shown}:{port}/api/account")
    log("the certificate is self-signed and trusted by nobody — a validating client must refuse it")
    log("press Ctrl+C to stop")

    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        while thread.is_alive():
            thread.join(0.5)
    except KeyboardInterrupt:
        log("stopping")
    finally:
        httpd.shutdown()
        httpd.server_close()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Self-signed HTTPS endpoint for the VulnDroid TLS lab."
    )
    parser.add_argument("--host", default=HOST, help="bind address (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=PORT, help="bind port (default: 8443)")
    parser.add_argument("--cert", default=None, help="PEM certificate (default: generated)")
    parser.add_argument("--key", default=None, help="PEM private key (default: generated)")
    parser.add_argument(
        "--generate-only",
        action="store_true",
        help="create the certificate and exit without serving",
    )
    args = parser.parse_args()

    cert_file = args.cert or CERT_FILE
    key_file = args.key or KEY_FILE

    if args.cert and args.key:
        if not (os.path.isfile(cert_file) and os.path.isfile(key_file)):
            raise SystemExit("the --cert/--key files do not exist")
        log(f"using existing certificate {cert_file}")
    else:
        if not (os.path.isfile(cert_file) and os.path.isfile(key_file)):
            generate_self_signed(cert_file, key_file, args.host)
        else:
            log(f"reusing certificate {cert_file}")

    if args.generate_only:
        log("done")
        return 0

    serve(args.host, args.port, cert_file, key_file)
    return 0


if __name__ == "__main__":
    sys.exit(main())
