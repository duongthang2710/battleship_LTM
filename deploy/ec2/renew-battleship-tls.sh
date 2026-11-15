#!/bin/sh
set -eu

lineage=/etc/letsencrypt/live/battleship
[ "${RENEWED_LINEAGE:-}" = "$lineage" ] || exit 0

umask 077
certificate=/etc/haproxy/battleship.pem
temporary=$(mktemp "$certificate.XXXXXX")
trap 'rm -f "$temporary"' EXIT
cat "$lineage/fullchain.pem" "$lineage/privkey.pem" > "$temporary"
mv -f "$temporary" "$certificate"

if systemctl is-active --quiet haproxy; then
    systemctl reload haproxy
fi
