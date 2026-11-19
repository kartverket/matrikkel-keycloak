"""Normalisert diff av to realm-eksporter. Lister hver sti som er endret (~), lagt til (+) eller fjernet (-).

Bruk: python diff.py for.json etter.json
ID-er og config-clis egne attributter ignoreres, og lister matches på navn (clientId, alias osv.) i stedet for posisjon.
"""
import json
import sys

# Lister av objekter matches på disse nøklene i stedet for posisjon.
KEYS = ("clientId", "alias", "name", "username", "flowAlias", "authenticator", "providerId")
IGNORE_ATTR_PREFIX = "de.adorsys.keycloak.config"


def key_of(o):
    if isinstance(o, dict):
        for k in KEYS:
            if k in o:
                extra = o.get("subType") or o.get("identityProviderAlias") or ""
                return f"{k}={o[k]}{'|' + extra if extra else ''}"
    return None


def norm(o, path=""):
    if isinstance(o, dict):
        return {
            k: norm(v, f"{path}.{k}")
            for k, v in o.items()
            if k not in ("id", "containerId", "createdTimestamp", "parentId", "flowId", "internalId")
            and not k.startswith(IGNORE_ATTR_PREFIX)
        }
    if isinstance(o, list):
        items = [norm(v, path) for v in o]
        keys = [key_of(v) for v in items]
        if items and all(keys) and len(set(keys)) == len(keys) and not path.endswith("authenticationExecutions"):
            return {k: v for k, v in zip(keys, items)}
        if all(not isinstance(v, (dict, list)) for v in items):
            return sorted(items, key=str)
        return items
    return o


def diff(a, b, path, out):
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b), key=str):
            p = f"{path}.{k}" if path else str(k)
            if k not in a:
                out.append(("+", p, b[k]))
            elif k not in b:
                out.append(("-", p, a[k]))
            else:
                diff(a[k], b[k], p, out)
    elif isinstance(a, list) and isinstance(b, list) and len(a) == len(b):
        for i, (x, y) in enumerate(zip(a, b)):
            diff(x, y, f"{path}[{i}]", out)
    elif a != b:
        out.append(("~", path, f"{json.dumps(a, ensure_ascii=False)[:120]} -> {json.dumps(b, ensure_ascii=False)[:120]}"))


before = norm(json.load(open(sys.argv[1], encoding="utf-8")))
after = norm(json.load(open(sys.argv[2], encoding="utf-8")))
changes = []
diff(before, after, "", changes)
if not changes:
    print("   0 endringer – config-jobben er no-op")
else:
    print(f"   {len(changes)} endringer:")
    for op, p, v in changes:
        print(f"   {op} {p}" + ("" if op != "~" else f"  {v}"))
