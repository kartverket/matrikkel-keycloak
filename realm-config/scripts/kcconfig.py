#!/usr/bin/env python3
"""Verktøy for realm-config (kun Python stdlib).

Kommandoer:
  sanitize  Saner en partial export fra Admin GUI (fjerner id-er, nøkler, brukere og secrets).
  split     Deler sanerte eksporter fra flere miljøer opp i felles base + miljø-overlays.
  render    Setter sammen base + overlay til én realm-fil per miljø (input til keycloak-config-cli).
  env-vars  Lister $(env:...)-variabler som en rendret fil krever.
  diff      Normalisert diff mellom to realm-filer (forventet vs. faktisk).
  export    Henter partial export fra en kjørende Keycloak via admin-API.
  drift     Eksporterer fra Keycloak og feiler hvis den avviker fra rendret konfig.
  seed      Oppretter realmen med Keycloaks egen import (CI / gjenoppretting fra scratch).

Se realm-config/README.md for bruk.
"""
import argparse
import copy
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

MASKED = "**********"
MISSING = object()
NOCHANGE = object()

# Felt som genereres av Keycloak og varierer mellom miljøer/kjøringer.
VOLATILE_KEYS = {"id", "containerId", "internalId", "keycloakVersion"}
# Ressurser som aldri skal i Git.
FORBIDDEN_TOP_LEVEL = {"users", "federatedUsers"}
KEY_PROVIDER = "org.keycloak.keys.KeyProvider"
CONFIG_CLI_ATTR_PREFIX = "de.adorsys.keycloak.config"
VOLATILE_CLIENT_ATTRS = {"client.secret.creation.time"}
# Klienter som eies av Keycloak. keycloak-config-cli feiler på realm-management (Keycloak nekter å lese secreten).
SYSTEM_CLIENTS = {"realm-management"}

# Hvilken base-/overlay-fil hver toppnøkkel hører hjemme i. Resten havner i realm.json.
FRAGMENTS = {
    "roles.json": ["roles", "defaultRole", "scopeMappings", "clientScopeMappings"],
    "client-scopes.json": ["clientScopes", "defaultDefaultClientScopes", "defaultOptionalClientScopes"],
    "authentication.json": [
        "authenticationFlows", "authenticatorConfig", "requiredActions",
        "browserFlow", "registrationFlow", "directGrantFlow", "resetCredentialsFlow",
        "clientAuthenticationFlow", "dockerAuthenticationFlow", "firstBrokerLoginFlow",
    ],
    "clients.json": ["clients"],
    "identity-providers.json": ["identityProviders", "identityProviderMappers"],
    "groups.json": ["groups"],
    "components.json": ["components"],
}
REALM_FRAGMENT = "realm.json"


# --------------------------------------------------------------------------- helpers

def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def dump(obj, path):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def key_fields(item):
    """Felt som identifiserer et element i en liste (f.eks. clientId for klienter)."""
    if not isinstance(item, dict):
        return None
    if isinstance(item.get("identityProviderAlias"), str) and isinstance(item.get("name"), str):
        return ("identityProviderAlias", "name")
    # Komponenter (f.eks. ClientRegistrationPolicy) har samme navn for ulike subType
    if isinstance(item.get("providerId"), str) and isinstance(item.get("subType"), str) \
            and isinstance(item.get("name"), str):
        return ("name", "subType")
    for k in ("clientId", "alias", "name", "client", "clientScope"):
        if isinstance(item.get(k), str):
            return (k,)
    return None


def item_key(item):
    fields = key_fields(item)
    if fields is None:
        return None
    return "/".join(str(item[f]) for f in fields)


def keyed_map(lst):
    """dict nøkkel->element hvis alle elementer har unik nøkkel, ellers None (listen er da atomisk)."""
    if not isinstance(lst, list):
        return None
    out = {}
    for item in lst:
        k = item_key(item)
        if k is None or k in out:
            return None
        out[k] = item
    return out


def is_keyed_list(*values):
    return all(isinstance(v, list) and v and keyed_map(v) is not None for v in values)


def normalize(obj, path=()):
    """Fjerner volatile felt og sorterer lister slik at to eksporter kan sammenlignes."""
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k in VOLATILE_KEYS or v == MASKED:
                continue
            if not path and k in FORBIDDEN_TOP_LEVEL:
                continue
            if k == "attributes" and isinstance(v, dict):
                v = {ak: av for ak, av in v.items()
                     if not ak.startswith(CONFIG_CLI_ATTR_PREFIX) and ak not in VOLATILE_CLIENT_ATTRS}
            out[k] = normalize(v, path + (k,))
        return dict(sorted(out.items()))
    if isinstance(obj, list):
        items = [normalize(v, path) for v in obj]
        if all(not isinstance(v, (dict, list)) for v in items):
            return sorted(items, key=lambda v: json.dumps(v))
        km = keyed_map(items)
        if km is not None:
            return [km[k] for k in sorted(km)]
        return items  # Rekkefølgen betyr noe (f.eks. authenticationExecutions)
    return obj


def realm_first(obj):
    if isinstance(obj, dict) and "realm" in obj:
        return {"realm": obj["realm"], **{k: v for k, v in obj.items() if k != "realm"}}
    return obj


# --------------------------------------------------------------------------- sanitize

def vault_key(*parts):
    return "-".join(re.sub(r"[^a-z0-9]+", "-", p.lower()).strip("-") for p in parts)


def sanitize(export, client_secret_env):
    data = copy.deepcopy(export)
    report = []

    for k in FORBIDDEN_TOP_LEVEL:
        if data.pop(k, None) is not None:
            report.append(f"fjernet '{k}'")

    if any(c.get("clientId") in SYSTEM_CLIENTS for c in data.get("clients", [])):
        data["clients"] = [c for c in data["clients"] if c.get("clientId") not in SYSTEM_CLIENTS]
        report.append(f"fjernet systemklient(er) {sorted(SYSTEM_CLIENTS)} (forvaltes av Keycloak)")
    client_roles = (data.get("roles") or {}).get("client") or {}
    for cid in SYSTEM_CLIENTS & set(client_roles):
        del client_roles[cid]
        report.append(f"fjernet innebygde klientroller for '{cid}' (beskyttede admin-roller)")

    # config-cli krever at authenticatorConfig brukes av en execution; foreldreløse configer er rester i Keycloak.
    used_configs = {e.get("authenticatorConfig") for f in data.get("authenticationFlows", [])
                    for e in f.get("authenticationExecutions", [])}
    for cfg in [c for c in data.get("authenticatorConfig", []) if c.get("alias") not in used_configs]:
        data["authenticatorConfig"].remove(cfg)
        report.append(f"ADVARSEL: fjernet authenticatorConfig '{cfg.get('alias')}' som ikke brukes av noen flow")

    for client in data.get("clients", []):
        cid = client.get("clientId")
        for binding, alias in resolve_flow_overrides(data, client):
            report.append(f"client '{cid}': flow-override {binding} -> alias '{alias}'")
        if cid in client_secret_env:
            client["secret"] = f"$(env:{client_secret_env[cid]})"
            report.append(f"client '{cid}': secret -> $(env:{client_secret_env[cid]})")
        elif "secret" in client:
            del client["secret"]
            if not client.get("publicClient") and not client.get("bearerOnly"):
                report.append(f"client '{cid}': secret fjernet (eies av Keycloak)")
        (client.get("attributes") or {}).pop("client.secret.creation.time", None)

    for idp in data.get("identityProviders", []):
        # Skrivebeskyttet felt avledet av providerId (KC 26.5+). keycloak-config-cli 6.5.1 avviser det.
        if idp.pop("types", None) is not None:
            report.append(f"idp '{idp['alias']}': fjernet skrivebeskyttet felt 'types'")
        cfg = idp.get("config") or {}
        if "clientSecret" in cfg:
            ref = "${vault." + vault_key("idp", idp["alias"], "client-secret") + "}"
            cfg["clientSecret"] = ref
            report.append(f"idp '{idp['alias']}': clientSecret -> {ref}")

    smtp = data.get("smtpServer") or {}
    if "password" in smtp:
        smtp["password"] = "${vault.smtp-password}"
        report.append("smtpServer.password -> ${vault.smtp-password}")

    comps = data.get("components") or {}
    if KEY_PROVIDER in comps:
        del comps[KEY_PROVIDER]
        report.append(f"fjernet components['{KEY_PROVIDER}'] (realm-nøkler)")

    leftovers = []

    def find_masked(o, p):
        if isinstance(o, dict):
            for k, v in o.items():
                find_masked(v, p + [k])
        elif isinstance(o, list):
            for i, v in enumerate(o):
                find_masked(v, p + [item_key(v) or str(i)])
        elif o == MASKED:
            leftovers.append("/".join(p))

    find_masked(data, [])
    for p in leftovers:
        report.append(f"ADVARSEL: maskert verdi fjernet uten erstatning: {p}")

    return realm_first(normalize(data)), report


# --------------------------------------------------------------------------- split / merge

def common(values):
    """Det som er likt i alle miljøer (blir base)."""
    first = values[0]
    if all(isinstance(v, dict) for v in values):
        out = {}
        for k in first:
            if all(k in v for v in values):
                c = common([v[k] for v in values])
                if c is not MISSING:
                    out[k] = c
        return out
    if is_keyed_list(*values):
        maps = [keyed_map(v) for v in values]
        out = []
        for k in maps[0]:
            if all(k in m for m in maps):
                c = common([m[k] for m in maps])
                if c is not MISSING:
                    out.append(c)
        return out
    return first if all(v == first for v in values) else MISSING


def overlay(env, base):
    """Det som må legges oppå base for å få miljøets konfigurasjon."""
    if base is MISSING:
        return env
    if isinstance(env, dict) and isinstance(base, dict):
        missing = set(base) - set(env)
        if missing:
            raise ValueError(f"base inneholder nøkler som mangler i miljøet: {sorted(missing)}")
        out = {}
        for k, v in env.items():
            d = overlay(v, base.get(k, MISSING))
            if d is not NOCHANGE:
                out[k] = d
        return out if out else NOCHANGE
    if is_keyed_list(env, base):
        em, bm = keyed_map(env), keyed_map(base)
        out = []
        for k, item in em.items():
            d = overlay(item, bm.get(k, MISSING))
            if d is NOCHANGE:
                continue
            if k in bm:
                d = {**{f: item[f] for f in key_fields(item)}, **d}
            out.append(d)
        return out if out else NOCHANGE
    return NOCHANGE if env == base else env


def merge(a, b):
    """Deep merge: objekter flettes, lister med nøkkel (clientId/alias/name) flettes per element,
    andre lister og verdier erstattes."""
    if isinstance(a, dict) and isinstance(b, dict):
        out = dict(a)
        for k, v in b.items():
            out[k] = merge(a[k], v) if k in a else copy.deepcopy(v)
        return out
    if is_keyed_list(a, b):
        am, bm = keyed_map(a), keyed_map(b)
        out = [merge(item, bm[k]) if k in bm else item for k, item in am.items()]
        out += [copy.deepcopy(item) for k, item in bm.items() if k not in am]
        return out
    return copy.deepcopy(b)


def fragment_for(key):
    for name, keys in FRAGMENTS.items():
        if key in keys:
            return name
    return REALM_FRAGMENT


def write_fragments(obj, directory):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=True)
    for old in directory.glob("*.json"):
        old.unlink()
    parts = {}
    for k, v in obj.items():
        parts.setdefault(fragment_for(k), {})[k] = v
    for name, part in parts.items():
        dump(realm_first(part), directory / name)
    return sorted(parts)


def read_fragments(directory):
    out = {}
    for f in sorted(Path(directory).glob("*.json")):
        out = merge(out, load(f))
    return out


def render_env(root, env):
    base_dir = Path(root) / "base"
    if not any(base_dir.glob("*.json")):
        raise SystemExit(f"Fant ingen base-filer i {base_dir}. Se realm-config/README.md (migrering).")
    env_dir = Path(root) / "envs" / env
    if not env_dir.is_dir():
        raise SystemExit(f"Ukjent miljø '{env}' (mangler {env_dir})")
    result = merge(read_fragments(base_dir), read_fragments(env_dir))
    if "realm" not in result:
        raise SystemExit("Rendret konfigurasjon mangler 'realm'")
    return realm_first(result)


# --------------------------------------------------------------------------- diff

def diff(a, b, path="", out=None):
    out = [] if out is None else out
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            p = f"{path}/{k}" if path else k
            if k not in b:
                out.append(("-", p, a[k], None))
            elif k not in a:
                out.append(("+", p, None, b[k]))
            else:
                diff(a[k], b[k], p, out)
        return out
    if is_keyed_list(a, b):
        am, bm = keyed_map(a), keyed_map(b)
        for k in sorted(set(am) | set(bm)):
            p = f"{path}[{k}]"
            if k not in bm:
                out.append(("-", p, am[k], None))
            elif k not in am:
                out.append(("+", p, None, bm[k]))
            else:
                diff(am[k], bm[k], p, out)
        return out
    if a != b:
        out.append(("~", path, a, b))
    return out


def read_ignore(path):
    if not path or not Path(path).exists():
        return []
    patterns = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            patterns.append(line)
    return patterns


def short(v):
    s = json.dumps(v, ensure_ascii=False)
    return s if len(s) <= 140 else s[:137] + "..."


# --------------------------------------------------------------------------- export (admin-API)

def http(url, data=None, headers=None, method=None):
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    with urllib.request.urlopen(req, timeout=60) as resp:  # noqa: S310 (URL styres av operatør)
        return json.loads(resp.read().decode("utf-8") or "null")


def get_token(url, login_realm, client_id, client_secret=None, username=None, password=None):
    form = {"client_id": client_id}
    if username:
        form.update(grant_type="password", username=username, password=password)
    else:
        form.update(grant_type="client_credentials", client_secret=client_secret)
    tok = http(f"{url}/realms/{login_realm}/protocol/openid-connect/token",
               data=urllib.parse.urlencode(form).encode(),
               headers={"Content-Type": "application/x-www-form-urlencoded"})
    return tok["access_token"]


def partial_export(url, realm, token):
    q = urllib.parse.urlencode({"exportClients": "true", "exportGroupsAndRoles": "true"})
    return http(f"{url}/admin/realms/{realm}/partial-export?{q}", data=b"",
                headers={"Authorization": f"Bearer {token}"}, method="POST")


# --------------------------------------------------------------------------- CLI

def cmd_sanitize(args):
    mapping = dict(m.split("=", 1) for m in args.client_secret_env)
    data, report = sanitize(load(args.input), mapping)
    dump(data, args.output)
    for line in report:
        print(f"  {line}")
    print(f"Skrev {args.output}")


def cmd_split(args):
    envs = {}
    for spec in args.env:
        name, path = spec.split("=", 1)
        envs[name] = normalize(load(path))
    names = list(envs)
    base = common([envs[n] for n in names])
    root = Path(args.root)
    print(f"base: {', '.join(write_fragments(realm_first(base), root / 'base'))}")
    for name in names:
        ov = overlay(envs[name], base)
        files = write_fragments({} if ov is NOCHANGE else ov, root / "envs" / name)
        print(f"envs/{name}: {', '.join(files) or '(ingen avvik fra base)'}")
        problems = diff(envs[name], normalize(render_env(root, name)))
        if problems:
            raise SystemExit(f"Kontroll feilet: render({name}) != eksport ({len(problems)} avvik)")
    print("Kontroll OK: render(base + overlay) == sanert eksport for alle miljøer")


def cmd_render(args):
    envs = args.env or sorted(p.name for p in (Path(args.root) / "envs").iterdir() if p.is_dir())
    for env in envs:
        out = Path(args.output) / env / "realm.json"
        dump(render_env(args.root, env), out)
        print(f"Skrev {out}")


def cmd_env_vars(args):
    text = Path(args.input).read_text(encoding="utf-8")
    for name in sorted(set(re.findall(r"\$\(env:([A-Za-z0-9_]+)\)", text))):
        print(name)


def resolve_flow_overrides(data, client):
    """Bytter flow-id-er i authenticationFlowBindingOverrides med alias (Git og config-cli bruker alias)."""
    alias_by_id = {f["id"]: f["alias"] for f in data.get("authenticationFlows", []) if "id" in f}
    overrides = client.get("authenticationFlowBindingOverrides") or {}
    changed = []
    for binding, flow_id in list(overrides.items()):
        if flow_id in alias_by_id:
            overrides[binding] = alias_by_id[flow_id]
            changed.append((binding, overrides[binding]))
    return changed


def ignore_regex(pattern):
    """Kun '*' er jokertegn; '[' og ']' er bokstavelige (brukes i stier som clients[x])."""
    return re.compile("^" + ".*".join(re.escape(part) for part in pattern.split("*")) + "$")


def report_diff(expected, actual, ignore_paths):
    ignore_paths = [p for p in (ignore_paths or []) if p]
    ignore = []
    for path in ignore_paths:
        for p in read_ignore(path):
            op, _, rest = p.partition(" ")
            # '+ sti' / '- sti' / '~ sti' treffer bare den typen avvik; uten prefiks treffes alle
            ignore.append((op, ignore_regex(rest)) if op in ("+", "-", "~") and rest else (None, ignore_regex(p)))
    for client in actual.get("clients", []):
        resolve_flow_overrides(actual, client)
    changes = diff(normalize(expected), normalize(actual))
    unexplained = [c for c in changes if not any((op is None or op == c[0]) and r.match(c[1]) for op, r in ignore)]
    for op, p, a, b in unexplained:
        if op == "~":
            print(f"~ {p}: {short(a)} -> {short(b)}")
        elif op == "-":
            print(f"- {p} (mangler i faktisk): {short(a)}")
        else:
            print(f"+ {p} (finnes kun i faktisk): {short(b)}")
    print(f"{len(unexplained)} uforklarte avvik, {len(changes) - len(unexplained)} forklart "
          f"via {', '.join(ignore_paths) or '(ingen ignore-fil)'}")
    return 1 if unexplained else 0


def cmd_diff(args):
    return report_diff(load(args.expected), load(args.actual), args.ignore)


def cmd_drift(args):
    expected = load(args.expected)
    token = get_token(args.url, args.login_realm, args.client_id, os.environ[args.client_secret_env])
    actual = partial_export(args.url, expected["realm"], token)
    rc = report_diff(expected, actual, args.ignore)
    print("DRIFT OPPDAGET: Keycloak avviker fra Git" if rc else "Ingen drift")
    return rc


def cmd_export(args):
    if args.client_secret_env:
        token = get_token(args.url, args.login_realm, args.client_id, os.environ[args.client_secret_env])
    else:
        token = get_token(args.url, args.login_realm, args.client_id,
                          username=os.environ["KC_EXPORT_USER"], password=os.environ["KC_EXPORT_PASSWORD"])
    dump(partial_export(args.url, args.realm, token), args.output)
    print(f"Skrev {args.output}")


def cmd_seed(args):
    """Oppretter realmen med Keycloaks egen import (POST /admin/realms).

    keycloak-config-cli kan ikke opprette built-in-flows. Eldre realmer har built-in-flows som nyere
    Keycloak ikke lager i nye realmer (f.eks. 'Browser - Conditional OTP'). Keycloaks egen import gjør det,
    så vi seeder med den og lar config-cli forvalte videre. Brukes i CI og ved gjenoppretting fra scratch.
    """
    rep = load(args.input)
    for client in rep.get("clients", []):
        # Overrides i Git refererer flow-alias; Keycloaks import forventer id. config-cli setter dem etterpå.
        client.pop("authenticationFlowBindingOverrides", None)
    token = get_token(args.url, "master", "admin-cli",
                      username=os.environ["KC_EXPORT_USER"], password=os.environ["KC_EXPORT_PASSWORD"])
    req = urllib.request.Request(f"{args.url}/admin/realms", data=json.dumps(rep).encode("utf-8"), method="POST",
                                 headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:  # noqa: S310
            print(f"Realm '{rep['realm']}' opprettet (HTTP {resp.status})")
    except urllib.error.HTTPError as e:
        raise SystemExit(f"Seed feilet (HTTP {e.code}): {e.read().decode('utf-8', 'replace')[:2000]}") from None


def main(argv=None):
    root_default = str(Path(__file__).resolve().parent.parent)
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("sanitize", help="saner en partial export")
    s.add_argument("input")
    s.add_argument("output")
    s.add_argument("--client-secret-env", action="append", default=[], metavar="CLIENTID=ENVVAR",
                   help="klient der secret injiseres fra env (GSM) i stedet for å eies av Keycloak")
    s.set_defaults(fn=cmd_sanitize)

    s = sub.add_parser("split", help="del sanerte eksporter i base + miljø-overlays")
    s.add_argument("--env", action="append", required=True, metavar="NAVN=FIL")
    s.add_argument("--root", default=root_default)
    s.set_defaults(fn=cmd_split)

    s = sub.add_parser("render", help="render base + overlay per miljø")
    s.add_argument("--env", action="append")
    s.add_argument("--root", default=root_default)
    s.add_argument("--output", default=str(Path(root_default) / "build"))
    s.set_defaults(fn=cmd_render)

    s = sub.add_parser("env-vars", help="list $(env:...)-variabler i en rendret fil")
    s.add_argument("input")
    s.set_defaults(fn=cmd_env_vars)

    s = sub.add_parser("diff", help="normalisert diff (forventet vs faktisk)")
    s.add_argument("expected")
    s.add_argument("actual")
    s.add_argument("--ignore", action="append", help="fil med mønstre for forklarte avvik (kan gis flere ganger)")
    s.set_defaults(fn=cmd_diff)

    s = sub.add_parser("export", help="partial export via admin-API")
    s.add_argument("--url", required=True, help="f.eks. http://localhost:8080/auth")
    s.add_argument("--realm", default="matrikkelen")
    s.add_argument("--login-realm", default="master")
    s.add_argument("--client-id", default="admin-cli")
    s.add_argument("--client-secret-env", help="env-var med klient-secret (client_credentials). "
                                               "Uten denne brukes KC_EXPORT_USER/KC_EXPORT_PASSWORD.")
    s.add_argument("output")
    s.set_defaults(fn=cmd_export)

    s = sub.add_parser("seed", help="opprett realm med Keycloaks egen import (KC_EXPORT_USER/PASSWORD)")
    s.add_argument("input", help="rendret realm-fil")
    s.add_argument("--url", required=True)
    s.set_defaults(fn=cmd_seed)

    s = sub.add_parser("drift", help="eksporter fra Keycloak og diff mot rendret konfig (exit 1 ved drift)")
    s.add_argument("expected", help="rendret realm-fil, f.eks. /config/dev/realm.json")
    s.add_argument("--url", default=os.environ.get("KEYCLOAK_URL"))
    s.add_argument("--login-realm", default=os.environ.get("KEYCLOAK_LOGINREALM", "master"))
    s.add_argument("--client-id", default=os.environ.get("KEYCLOAK_CLIENTID", "matrikkel-keycloak-config"))
    s.add_argument("--client-secret-env", default="KEYCLOAK_CLIENTSECRET")
    s.add_argument("--ignore", action="append")
    s.set_defaults(fn=cmd_drift)

    args = p.parse_args(argv)
    return args.fn(args) or 0


if __name__ == "__main__":
    sys.exit(main())
