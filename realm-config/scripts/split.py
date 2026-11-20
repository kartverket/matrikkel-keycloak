"""Migrerer et miljø: deler en realm-eksport opp i én fil per ressurstype for keycloak-config-cli, og fjerner
det som ikke skal i Git. Kjøres én gang per miljø. Etter det er filene i Git fasit og redigeres direkte.

Bruk (fra rota av repoet):
    python realm-config/scripts/split.py dev-etter.snapshot.json realm-config/envs/dev

Struktur per miljø (ingenting deles mellom miljøer):
    <miljø>/base/realm.json         realm-innstillinger
    <miljø>/resources/NN-<type>.json én fil per ressurstype

Regler:
- Hver ressurstype ligger i nøyaktig én fil. config-cli lagrer state per type, og to filer med samme type
  ville overskrive hverandres state og slette hverandres ressurser i full-modus.
- resources/ importeres i alfabetisk rekkefølge. Prefiksene sørger for at avhengigheter finnes først
  (client scopes og flows før klienter, klienter før klientroller osv.).
- base/ importeres etter resources/ (rekkefølgen står i IMPORT_FILES_LOCATIONS i Dockerfile). Hver fil gjør
  en realm-oppdatering, og Keycloak setter oauth2DevicePollingInterval tilbake til 5 når feltet mangler.
  Siste fil bestemmer verdien.
- Fjernes: ID-er, tidsstempler, maskerte secrets, realm-nøkler, config-cli-state og innebygde ressurser
  som Keycloak oppretter selv.
- Identity providers tas ikke med ennå, fordi secretene må gå via vault først. Se README.
"""
import json
import os
import sys

BUILTIN_CLIENTS = {"account", "account-console", "admin-cli", "broker", "realm-management", "security-admin-console"}
DROP_KEYS = {"id", "internalId", "containerId", "createdTimestamp", "notBefore"}
DROP_CLIENT_ATTRS = {"client.secret.creation.time", "client.secret.rotated", "client.secret.expiration.time"}
# Rester etter providers som ikke lenger finnes i imaget. Bør ryddes bort i miljøene.
UNKNOWN_PROVIDERS = {"default-roles"}
USER_KEYS = ["username", "enabled", "serviceAccountClientId", "attributes", "realmRoles", "clientRoles", "groups"]
FLOW_BINDINGS = ["browserFlow", "registrationFlow", "directGrantFlow", "resetCredentialsFlow",
                 "clientAuthenticationFlow", "dockerAuthenticationFlow", "firstBrokerLoginFlow"]
NOT_REALM_SETTINGS = {
    "id", "realm", "notBefore", "keycloakVersion", "defaultRole", "users", "groups", "roles", "clients",
    "clientScopes", "defaultDefaultClientScopes", "defaultOptionalClientScopes", "scopeMappings",
    "clientScopeMappings", "authenticationFlows", "authenticatorConfig", "requiredActions", "components",
    "identityProviders", "identityProviderMappers", "localizationTexts", *FLOW_BINDINGS,
}


def strip(obj):
    if isinstance(obj, dict):
        return {k: strip(v) for k, v in obj.items() if k not in DROP_KEYS}
    if isinstance(obj, list):
        return [strip(v) for v in obj]
    return obj


def realm_settings(d):
    out = {k: v for k, v in d.items() if k not in NOT_REALM_SETTINGS}
    out["attributes"] = {
        k: v for k, v in d.get("attributes", {}).items()
        if not k.startswith("de.adorsys.keycloak.config.") and not k.endswith(".lastExecutionTime")
    }
    return out


def client_scopes(d):
    return {
        "clientScopes": strip(sorted(d["clientScopes"], key=lambda s: s["name"])),
        "defaultDefaultClientScopes": sorted(d["defaultDefaultClientScopes"]),
        "defaultOptionalClientScopes": sorted(d["defaultOptionalClientScopes"]),
    }


def flows(d, warn):
    custom = [f for f in d["authenticationFlows"] if not f["builtIn"]]
    aliases = {f["alias"] for f in custom}
    used_configs = set()
    for f in custom:
        for e in f["authenticationExecutions"]:
            if e.get("authenticatorFlow") and e.get("flowAlias") not in aliases:
                warn(f"flow '{f['alias']}' bruker innebygd underflow '{e.get('flowAlias')}'")
            if e.get("authenticatorConfig"):
                used_configs.add(e["authenticatorConfig"])
    configs = [c for c in d["authenticatorConfig"] if c["alias"] in used_configs]
    for c in d["authenticatorConfig"]:
        if c["alias"] not in used_configs:
            warn(f"authenticatorConfig '{c['alias']}' er ikke brukt av egne flows og tas ikke med")
    out = {
        "authenticationFlows": strip(sorted(custom, key=lambda f: f["alias"])),
        "authenticatorConfig": strip(sorted(configs, key=lambda c: c["alias"])),
    }
    out.update({k: d[k] for k in FLOW_BINDINGS if k in d})
    return out


def clients(d):
    flow_alias = {f["id"]: f["alias"] for f in d["authenticationFlows"]}
    out = []
    for c in sorted(d["clients"], key=lambda c: c["clientId"]):
        if c["clientId"] in BUILTIN_CLIENTS:
            continue
        c = strip({k: v for k, v in c.items() if k != "secret"})
        if "attributes" in c:
            c["attributes"] = {k: v for k, v in c["attributes"].items() if k not in DROP_CLIENT_ATTRS}
        if c.get("authenticationFlowBindingOverrides"):
            c["authenticationFlowBindingOverrides"] = {
                k: flow_alias.get(v, v) for k, v in c["authenticationFlowBindingOverrides"].items()
            }
        out.append(c)
    return {"clients": out}


def roles(d):
    client_roles = {
        k: strip(sorted(v, key=lambda r: r["name"]))
        for k, v in sorted(d["roles"]["client"].items())
        if k not in BUILTIN_CLIENTS and v
    }
    return {"roles": {"realm": strip(sorted(d["roles"]["realm"], key=lambda r: r["name"])), "client": client_roles}}


def scope_mappings(d):
    return {"scopeMappings": d["scopeMappings"], "clientScopeMappings": dict(sorted(d["clientScopeMappings"].items()))}


def service_accounts(d):
    users = [u for u in d["users"] if u.get("serviceAccountClientId")]
    return {"users": [{k: u[k] for k in USER_KEYS if k in u} for u in sorted(users, key=lambda u: u["username"])]}


def components(d, warn):
    comps = {}
    for k, v in d["components"].items():
        if k in ("org.keycloak.keys.KeyProvider", "org.keycloak.userprofile.UserProfileProvider"):
            continue
        for c in v:
            if c.get("providerId") in UNKNOWN_PROVIDERS:
                warn(f"komponenten '{c['name']}' ({c['providerId']}) finnes ikke i imaget og tas ikke med")
        comps[k] = strip([c for c in v if c.get("providerId") not in UNKNOWN_PROVIDERS])
    return {"components": comps}


def user_profile(d):
    up = d["components"].get("org.keycloak.userprofile.UserProfileProvider", [])
    if not up:
        return None
    return {"userProfile": json.loads(up[0]["config"]["kc.user.profile.config"][0])}


def main():
    src, dst = sys.argv[1], sys.argv[2]
    d = json.load(open(src, encoding="utf-8"))
    warnings = []
    files = {
        "resources/20-client-scopes.json": client_scopes(d),
        "resources/30-authentication-flows.json": flows(d, warnings.append),
        "resources/35-required-actions.json": {"requiredActions": strip(d["requiredActions"])},
        "resources/40-clients.json": clients(d),
        "resources/50-roles.json": roles(d),
        "resources/60-scope-mappings.json": scope_mappings(d),
        "resources/70-service-accounts.json": service_accounts(d),
        "resources/80-components.json": components(d, warnings.append),
        "resources/85-user-profile.json": user_profile(d),
        "base/realm.json": realm_settings(d),
    }
    if d.get("groups"):
        files["resources/75-groups.json"] = {"groups": strip(d["groups"])}
    if d.get("identityProviders"):
        warnings.append(f"{len(d['identityProviders'])} identity providers er ikke tatt med (venter på vault)")
    for name, body in files.items():
        if body is None:
            continue
        os.makedirs(os.path.dirname(os.path.join(dst, name)), exist_ok=True)
        with open(os.path.join(dst, name), "w", encoding="utf-8", newline="\n") as f:
            json.dump({"realm": d["realm"], **body}, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"  {name}")
    for w in warnings:
        print(f"  OBS: {w}")


if __name__ == "__main__":
    main()
