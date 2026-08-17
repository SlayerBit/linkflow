#!/usr/bin/env sh
#
# Generates a self-signed certificate so the Compose stack can serve HTTPS locally.
#
# Self-signed is the right choice here and only here: it exercises the real TLS path — termination,
# HSTS, secure cookies, the HTTP-to-HTTPS redirect — without depending on a public DNS name or a
# certificate authority. Browsers will warn, which is correct and expected.
#
# For a real deployment, do not use this. Point certbot at infrastructure/nginx/certs instead; the
# HTTP server block already serves /.well-known/acme-challenge from /var/www/certbot for HTTP-01
# validation. See docs/DEPLOYMENT.md.

set -eu

CERT_DIR="$(dirname "$0")/certs"
DAYS=365
SUBJECT="/C=US/ST=Local/L=Local/O=LinkFlow Development/CN=localhost"

mkdir -p "$CERT_DIR"

if [ -f "$CERT_DIR/linkflow.crt" ] && [ "${FORCE:-}" != "1" ]; then
    echo "Certificate already present at $CERT_DIR/linkflow.crt"
    echo "Re-run with FORCE=1 to replace it."
    exit 0
fi

# ECDSA P-256 rather than RSA-2048: smaller handshake, and the cipher list in
# conf.d/linkflow.conf offers ECDSA suites first.
#
# The SAN matters — browsers have ignored CN for host verification for years, so a certificate
# without subjectAltName is rejected outright regardless of what CN says.
openssl req -x509 -nodes \
    -newkey ec \
    -pkeyopt ec_paramgen_curve:prime256v1 \
    -days "$DAYS" \
    -subj "$SUBJECT" \
    -addext "subjectAltName=DNS:localhost,DNS:linkflow.local,IP:127.0.0.1" \
    -keyout "$CERT_DIR/linkflow.key" \
    -out "$CERT_DIR/linkflow.crt"

# The key must not be world-readable. Nginx reads it as root before dropping privileges.
chmod 600 "$CERT_DIR/linkflow.key"
chmod 644 "$CERT_DIR/linkflow.crt"

echo "Wrote self-signed certificate valid for $DAYS days:"
echo "  $CERT_DIR/linkflow.crt"
echo "  $CERT_DIR/linkflow.key"
