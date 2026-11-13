#!/usr/bin/env bash
# Generate a fresh PhoneGuard ECDSA P-256 signing keypair locally.
# Keep phoneguard-feed-private-key.b64 secret. Never upload or send it in chat.
set -euo pipefail
command -v openssl >/dev/null || { echo "OpenSSL is required."; exit 1; }
umask 077
mkdir -p phoneguard-signing
cd phoneguard-signing
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out phoneguard-feed-private-key.pem
openssl req -new -x509 -key phoneguard-feed-private-key.pem -out phoneguard-release-cert.pem -days 3650 -sha256 -subj "/CN=PhoneGuard Release/ O=mehdiroomiany/ C=IR"
openssl pkey -in phoneguard-feed-private-key.pem -pubout -outform DER | base64 -w0 > phoneguard-public-key-der.b64
base64 -w0 phoneguard-feed-private-key.pem > phoneguard-feed-private-key.b64
echo
echo "Generated files in: $(pwd)"
echo "PRIVATE SECRET (do not share): phoneguard-feed-private-key.b64"
echo "PUBLIC KEY (safe to share):"
cat phoneguard-public-key-der.b64
echo
echo "CERTIFICATE (safe to share):"
cat phoneguard-release-cert.pem
echo
echo "Paste the CONTENTS of phoneguard-feed-private-key.b64 into GitHub Actions secret PHONEGUARD_FEED_SIGNING_KEY_B64."
echo "Do not commit this folder or send the private key to anyone."
