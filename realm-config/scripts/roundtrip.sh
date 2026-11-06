#!/usr/bin/env bash
# Round-trip-test av realm-config mot en efemær Keycloak (vårt image, dev-mem).
#
#   realm-config/scripts/roundtrip.sh <env> <keycloak-image> <config-image>
#
# 1. Starter Keycloak.
# 2. Oppretter realmen med Keycloaks egen import (seed). Speiler at realmen finnes i miljøene,
#    inkludert eldre built-in-flows som config-cli ikke kan opprette.
# 3. Appliserer master/master.json (apply-klient + read-only admingruppe).
# 4. Appliserer miljøets konfig to ganger med apply-klienten -> minste-privilegium + idempotens.
# 5. Eksporterer med apply-klienten og differ mot rendret konfig (diff-ignore.txt = forklarte avvik).
set -euo pipefail

ENV_NAME="${1:?miljø (dev|betatest|prodtest|prod)}"
KC_IMAGE="${2:?keycloak-image}"
CONFIG_IMAGE="${3:?config-image}"
PORT="${KC_PORT:-8080}"

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="${WORK_DIR:-$(mktemp -d)}"
NET="kc-roundtrip-${ENV_NAME}"
KC="kc-roundtrip-${ENV_NAME}"
APPLY_SECRET="roundtrip-apply-secret"

cleanup() {
  docker logs "$KC" > "$WORK/keycloak.log" 2>&1 || true
  if [ -n "${KEEP_KEYCLOAK:-}" ]; then echo "Keycloak kjører fortsatt: http://localhost:$PORT/auth (admin/admin)"; return; fi
  docker rm -f "$KC" > /dev/null 2>&1 || true
  docker network rm "$NET" > /dev/null 2>&1 || true
  echo "Artefakter: $WORK"
}
trap cleanup EXIT

python3 "$DIR/scripts/kcconfig.py" render --root "$DIR" --env "$ENV_NAME" --output "$WORK/rendered"
EXPECTED="$WORK/rendered/$ENV_NAME/realm.json"
REALM_NAME="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["realm"])' "$EXPECTED")"

# Dummy-verdier for alle $(env:...) i konfigurasjonen (ekte verdier kommer fra GSM i klyngen)
DUMMY_ENV=()
for var in $(python3 "$DIR/scripts/kcconfig.py" env-vars "$EXPECTED"); do
  DUMMY_ENV+=(-e "$var=roundtrip-dummy-$var")
done

docker network create "$NET" > /dev/null
docker run -d --name "$KC" --network "$NET" -p "$PORT:8080" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -e KC_HTTP_ENABLED=true \
  -e KC_HOSTNAME_STRICT=false \
  -e KEYCLOAK_EMAIL_OAUTH_TENANT_ID=roundtrip \
  -e KEYCLOAK_EMAIL_OAUTH_CLIENT_ID=roundtrip \
  -e KEYCLOAK_EMAIL_OAUTH_SECRET_ID=roundtrip \
  -e KEYCLOAK_EMAIL_OAUTH_USER_ID=roundtrip \
  "$KC_IMAGE" > /dev/null

KC_URL="http://$KC:8080/auth"
HOST_URL="http://localhost:$PORT/auth"
ADMIN=(-e KEYCLOAK_URL="$KC_URL" -e KEYCLOAK_GRANTTYPE=password -e KEYCLOAK_CLIENTID=admin-cli
       -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin)
APPLY=(-e KEYCLOAK_URL="$KC_URL" -e KEYCLOAK_CLIENTSECRET="$APPLY_SECRET" -e CONFIG_ENV="$ENV_NAME")

echo "== Venter på Keycloak"
for _ in $(seq 1 90); do
  curl -sf "$HOST_URL/realms/master" > /dev/null && break
  sleep 2
done
curl -sf "$HOST_URL/realms/master" > /dev/null || { echo "Keycloak startet ikke"; exit 1; }

echo "== 1/5 Seeder realm med Keycloaks egen import"
KC_EXPORT_USER=admin KC_EXPORT_PASSWORD=admin python3 "$DIR/scripts/kcconfig.py" seed "$EXPECTED" --url "$HOST_URL"

echo "== 2/5 Appliserer master-bootstrap (apply-klient)"
docker run --rm --network "$NET" "${ADMIN[@]}" -e IMPORT_FILES_LOCATIONS='file:/master/*.json' \
  -e KEYCLOAK_CONFIG_CLIENT_SECRET="$APPLY_SECRET" -e REALM_NAME="$REALM_NAME" "$CONFIG_IMAGE"

echo "== 3/5 Appliserer $ENV_NAME med apply-klienten (minste-privilegium)"
docker run --rm --network "$NET" "${APPLY[@]}" "${DUMMY_ENV[@]}" "$CONFIG_IMAGE"

echo "== 4/5 Appliserer $ENV_NAME på nytt (idempotens)"
docker run --rm --network "$NET" "${APPLY[@]}" "${DUMMY_ENV[@]}" "$CONFIG_IMAGE"

echo "== 5/5 Eksporterer og sammenligner"
APPLY_SECRET_VALUE="$APPLY_SECRET" python3 "$DIR/scripts/kcconfig.py" export \
  --url "$HOST_URL" --realm "$REALM_NAME" --client-id matrikkel-keycloak-config \
  --client-secret-env APPLY_SECRET_VALUE "$WORK/actual.json"
python3 "$DIR/scripts/kcconfig.py" diff "$EXPECTED" "$WORK/actual.json" --ignore "$DIR/diff-ignore.txt" --ignore "$DIR/roundtrip-ignore.txt"
