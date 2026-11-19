"""Feiler hvis noe som ikke skal i Git ligger i realm-config/envs/ eller realm-config/master/.

Bruk (fra rota av repoet): python realm-config/scripts/check.py
Reglene står i realm-config/README.md, «Hva som ikke skal i Git».
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SECRET_KEYS = {"secret", "clientSecret", "password", "privateKey", "keystorePassword", "truststorePassword"}
# Secrets er bare lov som referanse: $(env:X) i config-jobben eller ${vault.x} i Keycloak.
SECRET_REF = re.compile(r"^(\$\(env:[A-Za-z0-9_]+\)|\$\{vault\.[A-Za-z0-9_.-]+\})$")
GENERATED_KEYS = {"id", "internalId", "containerId", "createdTimestamp"}
USER_KEYS = {"username", "enabled", "serviceAccountClientId", "attributes", "realmRoles", "clientRoles", "groups"}
ALLOWED_EMAILS = {"ikke-svar@kartverket.no"}
EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
FNR = re.compile(r"(?<![\w-])\d{11}(?![\w-])")


def walk(obj, path, errors):
    if isinstance(obj, dict):
        for k, v in obj.items():
            p = f"{path}.{k}"
            if k in SECRET_KEYS and isinstance(v, str) and not SECRET_REF.match(v):
                errors.append(f"{p}: secret i klartekst (bruk $(env:...) eller ${{vault.x}})")
            if k in GENERATED_KEYS:
                errors.append(f"{p}: generert felt, referer med navn/alias i stedet")
            if k.startswith("de.adorsys.keycloak.config."):
                errors.append(f"{p}: config-cli-state")
            if k == "org.keycloak.keys.KeyProvider":
                errors.append(f"{p}: realm-nøkler")
            walk(v, p, errors)
    elif isinstance(obj, list):
        for i, v in enumerate(obj):
            walk(v, f"{path}[{i}]", errors)
    elif isinstance(obj, str):
        if "**********" in obj:
            errors.append(f"{path}: maskert secret fra eksport")
        for e in EMAIL.findall(obj):
            if e.lower() not in ALLOWED_EMAILS:
                errors.append(f"{path}: e-postadresse '{e}' (legg i ALLOWED_EMAILS hvis den ikke er personlig)")
        if FNR.search(obj):
            errors.append(f"{path}: 11 sifre, mulig fødselsnummer")


def check_users(doc, errors):
    for i, u in enumerate(doc.get("users") or []):
        p = f"users[{i}]"
        if not u.get("serviceAccountClientId"):
            errors.append(f"{p}: personlig bruker ({u.get('username')}), bare service accounts er lov")
        for k in set(u) - USER_KEYS:
            errors.append(f"{p}.{k}: ikke tillatt på brukere")


def main():
    files = sorted(glob.glob(os.path.join(ROOT, "envs", "**", "*.json"), recursive=True)
                   + glob.glob(os.path.join(ROOT, "master", "*.json")))
    failed = 0
    for f in files:
        rel = os.path.relpath(f, os.path.join(ROOT, ".."))
        errors = []
        with open(f, encoding="utf-8") as fh:
            doc = json.load(fh)
        walk(doc, "", errors)
        check_users(doc, errors)
        for e in errors:
            print(f"{rel}: {e}")
        failed += len(errors)
    print(f"{len(files)} filer sjekket, {failed} feil")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
