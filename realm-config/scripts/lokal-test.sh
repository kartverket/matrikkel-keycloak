#!/usr/bin/env bash
# Lokal test av realm-config: starter Keycloak, oppretter realmen, kjører bootstrap og config-jobben,
# og viser en diff av realmen før og etter jobben.
#
# Bruk (Git Bash, fra rota av repoet):
#   bash realm-config/scripts/lokal-test.sh dev.snapshot.json  # realm lik et miljø (no-op-test)
#   bash realm-config/scripts/lokal-test.sh                    # tom realm (feiler for dev, se README)
#
# Snapshot = en realm-eksport fra miljøet (se README). Den inneholder miljødata og skal aldri committes.
# Forutsetter at matrikkel-keycloak:local er bygget (se README).
set -euo pipefail

SNAPSHOT="${1:-}"
CONFIG_ENV="${CONFIG_ENV:-dev}"
REALM=matrikkelen-test
NET=kc-local
URL_HOST=http://localhost:8080/auth
URL_NET=http://kc-local:8080/auth
SCRIPTS="$(cd "$(dirname "$0")" && pwd)"
OUT="$(cd "$SCRIPTS/.." && pwd)/local"
mkdir -p "$OUT"
PY=$(command -v python3 >/dev/null 2>&1 && python3 -c '' 2>/dev/null && echo python3 || echo python)

docker image inspect matrikkel-keycloak:local >/dev/null 2>&1 \
  || { echo "Fant ikke matrikkel-keycloak:local. Bygg det først (se README)."; exit 1; }

admin_token() {
  curl -s -d client_id=admin-cli -d username=admin -d password=admin -d grant_type=password \
    $URL_HOST/realms/master/protocol/openid-connect/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/'
}

export_realm() {
  curl -sf -X POST -H "Authorization: Bearer $(admin_token)" \
    "$URL_HOST/admin/realms/$REALM/partial-export?exportClients=true&exportGroupsAndRoles=true" -o "$1"
  echo "   eksportert realm til $1"
}

# Eksporten maskerer klient-secrets, så diffen ser dem ikke. Lagrer et kort hash per konfidensiell klient.
client_secrets() {
  local tok
  tok=$(admin_token)
  curl -sf -H "Authorization: Bearer $tok" "$URL_HOST/admin/realms/$REALM/clients?max=1000" -o "$OUT/clients.json"
  $PY -c "import json,sys; [print(c['id'], c['clientId']) for c in json.load(open(sys.argv[1], encoding='utf-8')) if not c.get('publicClient') and not c.get('bearerOnly') and c.get('clientAuthenticatorType', 'client-secret') == 'client-secret']" "$OUT/clients.json" \
    | tr -d '\r' | while read -r id cid; do
      s=$(curl -sf -H "Authorization: Bearer $tok" "$URL_HOST/admin/realms/$REALM/clients/$id/client-secret" | sed -E 's/.*"value":"([^"]*)".*/\1/')
      echo "$cid $(printf '%s' "$s" | sha256sum | cut -c1-12)"
    done | sort > "$1"
}

# Snapshot fra eksport har maskerte (like) secrets. Gir hver konfidensiell klient en unik secret,
# slik at sjekken i steg 7 kan oppdage om jobben endrer eller tømmer dem.
regenerate_secrets() {
  local tok
  tok=$(admin_token)
  curl -sf -H "Authorization: Bearer $tok" "$URL_HOST/admin/realms/$REALM/clients?max=1000" -o "$OUT/clients.json"
  $PY -c "import json,sys; [print(c['id']) for c in json.load(open(sys.argv[1], encoding='utf-8')) if not c.get('publicClient') and not c.get('bearerOnly') and c.get('clientAuthenticatorType', 'client-secret') == 'client-secret']" "$OUT/clients.json" \
    | tr -d '\r' | while read -r id; do
      curl -sf -o /dev/null -X POST -H "Authorization: Bearer $tok" "$URL_HOST/admin/realms/$REALM/clients/$id/client-secret"
    done
}

echo "== 1. Bygger config-imaget"
docker build -q -t matrikkel-keycloak-config:local realm-config/

echo "== 2. Starter Keycloak (sletter eventuell gammel kc-local)"
docker rm -f kc-local >/dev/null 2>&1 || true
docker network inspect $NET >/dev/null 2>&1 || docker network create $NET >/dev/null
docker run -d --name kc-local --network $NET -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -e KC_HTTP_ENABLED=true -e KC_HOSTNAME_STRICT=false \
  -e KEYCLOAK_EMAIL_OAUTH_TENANT_ID=x -e KEYCLOAK_EMAIL_OAUTH_CLIENT_ID=x \
  -e KEYCLOAK_EMAIL_OAUTH_SECRET_ID=x -e KEYCLOAK_EMAIL_OAUTH_USER_ID=x \
  matrikkel-keycloak:local >/dev/null
for _ in $(seq 1 60); do curl -sf $URL_HOST/realms/master >/dev/null && break; sleep 3; done
curl -sf $URL_HOST/realms/master >/dev/null || { echo "Keycloak startet ikke"; docker logs --tail 30 kc-local; exit 1; }
echo "   klar"

# Realmen opprettes med Keycloaks egen import, ikke config-cli: realmen finnes alltid fra før i miljøene,
# og config-cli kan ikke opprette eldre innebygde flows som finnes i miljøene.
if [ -n "$SNAPSHOT" ]; then
  echo "== 3. Oppretter $REALM fra $SNAPSHOT"
  BODY=(--data-binary @"$SNAPSHOT")
else
  echo "== 3. Oppretter tom $REALM"
  BODY=(-d "{\"realm\":\"$REALM\",\"enabled\":true}")
fi
CODE=$(curl -s -o "$OUT/post.txt" -w "%{http_code}" -X POST -H "Authorization: Bearer $(admin_token)" \
  -H "Content-Type: application/json" "${BODY[@]}" $URL_HOST/admin/realms)
[ "$CODE" = "201" ] || { echo "POST feilet: $CODE"; cat "$OUT/post.txt"; docker logs --tail 20 kc-local 2>&1 | grep -E 'ERROR|Exception' || true; exit 1; }
echo "   201"

echo "== 3b. Eksporterer realmen før config-jobben"
regenerate_secrets
export_realm "$OUT/for.json"
client_secrets "$OUT/secrets-for.txt"

echo "== 4. Bootstrap av apply-klienten (master.json)"
MSYS_NO_PATHCONV=1 docker run --rm --network $NET -e KEYCLOAK_URL=$URL_NET \
  -e KEYCLOAK_GRANTTYPE=password -e KEYCLOAK_CLIENTID=admin-cli -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin \
  -e IMPORT_FILES_LOCATIONS='file:/master/*.json' -e KEYCLOAK_CONFIG_CLIENT_SECRET=lokal -e REALM_NAME=$REALM \
  matrikkel-keycloak-config:local 2>&1 | tee "$OUT/bootstrap.log" | grep -E 'Using version|ERROR|Exception|ran in' || true
grep -q ERROR "$OUT/bootstrap.log" && { echo "FEILET – se $OUT/bootstrap.log"; exit 1; }

echo "== 5. Config-jobben (som i SKIP, CONFIG_ENV=$CONFIG_ENV)"
docker run --rm --network $NET -e KEYCLOAK_URL=$URL_NET \
  -e KEYCLOAK_CLIENTSECRET=lokal -e CONFIG_ENV=$CONFIG_ENV matrikkel-keycloak-config:local 2>&1 \
  | tee "$OUT/apply.log" | grep -E 'Using version|ERROR|Exception|ran in' || true
grep -q ERROR "$OUT/apply.log" && { echo "FEILET – se $OUT/apply.log"; exit 1; }

echo "== 6. Diff før/etter config-jobben"
export_realm "$OUT/etter.json"
PYTHONIOENCODING=utf-8 $PY "$SCRIPTS/diff.py" "$OUT/for.json" "$OUT/etter.json"

echo "== 7. Klient-secrets før/etter (hash)"
client_secrets "$OUT/secrets-etter.txt"
if diff -q "$OUT/secrets-for.txt" "$OUT/secrets-etter.txt" >/dev/null; then
  echo "   uendret for $(wc -l < "$OUT/secrets-etter.txt") konfidensielle klienter"
else
  echo "   ENDRET:"; diff "$OUT/secrets-for.txt" "$OUT/secrets-etter.txt" | sed 's/^/   /' || true
fi
echo "== OK. Admin GUI: http://localhost:8080/auth (admin/admin). Rydd opp: docker rm -f kc-local; docker network rm kc-local"
