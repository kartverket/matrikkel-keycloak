# realm-config – Keycloak-konfigurasjon som kode

Realm-konfigurasjonen (realm-innstillinger, roller, client scopes, authentication flows,
klienter, identity providers med mappers og grupper) ligger i Git. Den appliseres med
[keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli) som en SKIPJob i hvert miljø.

Admin GUI brukes **ikke** til konfigurasjonsendringer. Brukeradministrasjon gjøres fortsatt der.

## Struktur

```
realm-config/
├── base/                 felles for alle miljøer (se base/README.md)
├── envs/<miljø>/         avvik per miljø: dev, betatest, prodtest, prod
├── master/master.json    bootstrap: apply-klient og read-only admingruppe i master-realmen
├── raw/                  råeksporter under migrering (ignorert av Git)
├── scripts/kcconfig.py   sanitize | split | render | diff | export | seed | drift (bare Python stdlib)
├── scripts/roundtrip.sh  round-trip-test mot en efemær Keycloak (CI og lokalt)
├── diff-ignore.txt       forklarte avvik mellom Git og eksport (hver linje har en begrunnelse)
├── roundtrip-ignore.txt  forklarte avvik som bare gjelder CI (realm opprettet fra scratch)
└── Dockerfile            bygger imagene matrikkel-keycloak-config (apply) og -config-drift
```

`render` fletter `base/*.json` og `envs/<miljø>/*.json` til `/config/<miljø>/realm.json` i imaget.
Lister flettes etter nøkkel (`clientId`, `alias`, `name` osv.).

## Endringsprosess

1. Endre JSON i `base/`, eller i `envs/<miljø>/` hvis endringen bare gjelder ett miljø.
   Tips: gjør endringen i Keycloak lokalt, ta en partial export og kopier inn det som er endret.
2. Kjør round-trip lokalt (se under), eller la CI gjøre det i PR-en.
3. Merge til `main`. `main.yml` bygger config-imaget og deployer SKIPJob-en til dev, betatest og
   prodtest etter Keycloak.
4. Prod følger med `promote-production.yml` sammen med Keycloak-imaget.

Config-jobbene i workflowene er av til repo-variabelen `REALM_CONFIG_DEPLOY` er satt til `true`.
Sett den først når SKIPJob-ene finnes i bygning-apps.

## Migrering (første gang)

Eksporten tas uten nedetid. Partial export endrer ingenting i Keycloak.

1. **Eksporter** hvert miljø: Admin GUI → Realm settings → Action → Partial export, med
   *groups and roles* og *clients* huket av. Lagre som `realm-config/raw/<miljø>.json`.
   Alternativt via API:
   ```shell
   KC_EXPORT_USER=... KC_EXPORT_PASSWORD=... python realm-config/scripts/kcconfig.py export \
     --url https://<host>/auth realm-config/raw/dev.json
   ```
2. **Saner** hver eksport. Kommandoen fjerner id-er, realm-nøkler, brukere og maskerte secrets.
   IdP- og SMTP-secrets blir `${vault.*}`.
   ```shell
   for env in dev betatest prodtest prod; do
     python realm-config/scripts/kcconfig.py sanitize realm-config/raw/$env.json realm-config/raw/$env.sanitized.json \
       --client-secret-env schedule-job=SCHEDULE_JOB_CLIENT_SECRET
   done
   ```
   Les advarslene. Alle `**********` som ikke er håndtert, er secrets som må få en `$(env:...)`
   eller `${vault...}`.
3. **Del opp** i base og overlays. Kommandoen kontrollerer at `render == eksport` for hvert miljø:
   ```shell
   python realm-config/scripts/kcconfig.py split \
     --env dev=realm-config/raw/dev.sanitized.json --env betatest=realm-config/raw/betatest.sanitized.json \
     --env prodtest=realm-config/raw/prodtest.sanitized.json --env prod=realm-config/raw/prod.sanitized.json
   ```
4. **Se gjennom** `base/` og `envs/` før commit. Søk etter `secret`, `password`, `privateKey` og
   e-postadresser. Commit aldri `raw/`.
5. **Round-trip** i CI, eller lokalt. Akseptkriteriet er 0 uforklarte avvik mot prod-eksporten:
   ```shell
   python realm-config/scripts/kcconfig.py render --env prod
   python realm-config/scripts/kcconfig.py diff realm-config/build/prod/realm.json \
     realm-config/raw/prod.sanitized.json --ignore realm-config/diff-ignore.txt
   ```
6. Slett `raw/` lokalt.

## Secrets

Google Secret Manager (GSM) er eneste kilde. Ingen secrets ligger i Git.

| Type | I Git | Hvordan verdien kommer fram |
|------|-------|-----------------------------|
| Klient-secret eid av Keycloak | utelatt | Keycloak genererer den. Konsumenten henter den fra Keycloak og lagrer den i GSM. |
| Klient-secret injisert (f.eks. `schedule-job`) | `"secret": "$(env:SCHEDULE_JOB_CLIENT_SECRET)"` | GSM → ExternalSecret → env i config-SKIPJob-en (og scheduler-jobben) |
| IdP client secret | `"clientSecret": "${vault.idp-<alias>-client-secret}"` | GSM → ExternalSecret → fil i Keycloak-podden (vault) |
| SMTP-passord | `"password": "${vault.smtp-password}"` | samme som over |
| Apply-klientens secret | `$(env:KEYCLOAK_CONFIG_CLIENT_SECRET)` i `master/master.json` | GSM → ExternalSecret → `KEYCLOAK_CLIENTSECRET` i SKIPJob-en |

`$(env:X)` løses av keycloak-config-cli når jobben kjører. `${vault.x}` lagres bokstavelig i
Keycloak og løses av Keycloaks innebygde **filbaserte vault** når verdien brukes. Det er ikke
HashiCorp Vault. Keycloak må startes med `KC_VAULT=file` og `KC_VAULT_DIR=<mount>`. Filnavnet er
`<realm>_<nøkkel>`, for eksempel `matrikkelen-test_idp-kartverket-client-secret`. Understrek i realm eller
nøkkel skrives som `__`.

## Utrulling i faser

| Fase | Hva | Hvordan |
|------|-----|---------|
| 1 | Adopsjon, no-op | Alle `IMPORT_MANAGED_*=no-delete` (standard i imaget). Første kjøring i dev skal ikke gi endringer. Sjekk det med `drift` før og etter. |
| 2 | Full forvaltning | Sett `IMPORT_MANAGED_<TYPE>=full` i SKIPJob-en for én type om gangen, for eksempel `CLIENTSCOPE`, `ROLE`, `GROUP`, `IDENTITYPROVIDERMAPPER`, `AUTHENTICATIONFLOW` og `CLIENT`. Verifiser mellom hver type. |
| 3 | prodtest, prod | Samme rekkefølge etter at dev og betatest er stabile. |

`IMPORT_MANAGED_COMPONENT` og `SUBCOMPONENT` skal **forbli `no-delete`**, fordi realm-nøkler ikke
ligger i Git. `IMPORT_REMOTESTATE_ENABLED=true` gjør at `full` bare sletter ressurser som
config-cli selv har opprettet. Ressurser som ble opprettet i GUI før adopsjonen, blir ikke slettet.

Røyktest etter hver kjøring: innlogging med ID-porten og Azure AD, direct grant og schedule-job.

## Tilgang og break-glass

`master/master.json` oppretter:

- **`matrikkel-keycloak-config`**: konfidensiell klient i master-realmen med bare service account.
  Den har rollene `view/manage-realm`, `-clients`, `-identity-providers`, `-authorization` og
  `-events`, `view/query-users`, `query-groups` og `manage-users` på `<realm>-realm`.
  Brukes av apply- og drift-jobbene.
- **`matrikkel-readonly-admins`**: gruppe for personlige admin-kontoer. Gruppen gir view-roller
  og `manage-users`, men ingen `manage-realm`, `-clients` eller `-identity-providers`. Admin GUI
  blir dermed read-only for konfigurasjon, mens brukeradministrasjon fortsatt virker.

Bootstrap kjøres manuelt én gang per miljø med master-admin, og ellers når `master.json` endres:

```shell
docker run --rm -e KEYCLOAK_URL=https://<host>/auth \
  -e KEYCLOAK_GRANTTYPE=password -e KEYCLOAK_CLIENTID=admin-cli \
  -e KEYCLOAK_USER=<break-glass> -e KEYCLOAK_PASSWORD=<fra GSM> \
  -e IMPORT_FILES_LOCATIONS='file:/master/*.json' \
  -e KEYCLOAK_CONFIG_CLIENT_SECRET=<fra GSM> -e REALM_NAME=<realm, f.eks. matrikkelen-test> \
  ghcr.io/kartverket/matrikkel-keycloak-config:<versjon>
```

**Break-glass:** Kontoen ligger i master-realmen, og credentials ligger i GSM. Bare
driftsansvarlige har tilgang.

1. Hent credentials fra GSM. Tilgangen logges.
2. Gjør den minimale endringen i Admin GUI.
3. Lag en PR som gjør den samme endringen i `realm-config/` **samme dag**. Ellers rapporterer
   drift-jobben avvik, og neste apply overskriver endringen.
4. Roter passordet etterpå.

## Drift-deteksjon

- **Nattlig eksport-diff:** imaget `matrikkel-keycloak-config-drift` kjører
  `kcconfig.py drift /config/<miljø>/realm.json` som cron-SKIPJob. Jobben feiler med exit 1 og
  normalisert diff hvis Keycloak avviker fra Git. Varsling settes opp på feilede jobber.
- **Admin events:** slå på *Admin events* (med representation) i realmen. Varsling på
  konfigurasjonsendringer gjort av andre enn `service-account-matrikkel-keycloak-config` er
  **TODO**, via Keycloak-loggene i Grafana/Loki.

## Lokal kjøring

Krever Docker, bash og Python 3.

```shell
# På Windows: bygg fra git archive med LF, ellers feiler gradlew (CRLF) med exit 127
git -c core.autocrlf=false -c core.eol=lf archive HEAD | docker build --build-arg KC_DB=dev-mem -t matrikkel-keycloak:roundtrip -
docker build --target apply -t matrikkel-keycloak-config:roundtrip realm-config/
# uten dhi.io-innlogging: legg til --build-arg PYTHON_BUILD_IMAGE=python:3.13-slim
realm-config/scripts/roundtrip.sh dev matrikkel-keycloak:roundtrip matrikkel-keycloak-config:roundtrip
```

Skriptet starter vårt Keycloak-image (dev-mem). Deretter kjører det disse stegene:

1. Seeder realmen.
2. Appliserer `master.json`.
3. Appliserer miljøet to ganger med apply-klienten. Dette tester minst mulige rettigheter og
   idempotens.
4. Eksporterer og differ mot `diff-ignore.txt` og `roundtrip-ignore.txt`. Den siste inneholder
   standardverdier som Keycloak bare setter når noe opprettes fra scratch, og gjelder bare CI.

Med `KEEP_KEYCLOAK=1` får Keycloak fortsette å kjøre på `localhost:8080` (admin/admin) etterpå,
slik at du kan feilsøke.

**Gjenoppretting fra scratch:** opprett realmen med `kcconfig.py seed <rendret realm.json> --url ...`
og kjør deretter apply-jobben. Seed bruker Keycloaks egen import. Se PoC-funn.

## Verktøyvalg og PoC-funn

keycloak-config-cli er valgt framfor Terraform (`mrparkers/keycloak`/`keycloak/keycloak`):

- Det bruker samme format som realm-eksporten. Migreringen blir derfor eksport → saner → split,
  uten egen modellering.
- Egne providers fungerer uten videre, fordi de bare er strenger i JSON-en: `flow-return-oidc`,
  `mat-choose-idp`, `matrikkel-aktivering-validate`, `matrikkel-midlertidig-bruker`,
  `mat-cond-programvarebruker`, `mat-cond-client-network`, `idp-choose-user-name` og
  `oidc-broker-id-hashed-mapper`. Terraform krever generiske ressurser per type og har svak
  støtte for flows.
- Det kjører som en vanlig container eller SKIPJob, uten state-backend. Remote state lagres i
  realm-attributter.

Round-trip med den ekte eksporten fra testmiljøet (realm `matrikkelen-test`, KC 26.8.1: 25 klienter,
3 IdP-er, 40 flows) ga **0 uforklarte avvik**. Kjeden var sanitize → split → render → seed → apply ×2 med
apply-klienten → eksport → diff. Funn:

1. **Eldre built-in-flows:** realmer som er oppgradert fra eldre Keycloak har built-in-flows som en
   fersk KC 26 ikke lager, for eksempel «Browser - Conditional OTP». config-cli kan ikke opprette
   built-in-flows. Det er ikke noe problem i miljøene, der realmen finnes. CI og gjenoppretting
   fra scratch bruker derfor `seed` først.
2. **Versjonskobling:** config-cli avviser felt som er ukjente for Keycloak-versjonen det er bygget mot.
   Det kan ikke slås av. Det nyeste ferdigbygde imaget (`6.5.1-26.5.5`) feilet på `maxSecondaryAuthFailures`
   fra KC 26.8. Derfor **bygges config-cli fra kildekode** i `Dockerfile` (fast tag og sjekksum) mot
   `KEYCLOAK_VERSION`. Den build-argumentet **må oppdateres sammen med Keycloak-imaget**. Round-trip i CI
   fanger opp brudd. IdP-feltet `types` fjernes av `sanitize`.
3. **Cache:** `IMPORT_CACHE_ENABLED=false`. Ellers hopper config-cli over uendrede filer, og drift
   blir aldri rettet opp.
4. **Udefinerte variabler:** `$(env:X)` uten verdi gjør at jobben feiler (`UNDEFINEDISERROR=true`).
   Det er bevisst. CI setter dummy-verdier.
5. **Realm-nøkler** (`KeyProvider`-komponenter) ligger aldri i Git og forvaltes av Keycloak.
6. **`realm-management`:** systemklienten og de innebygde admin-rollene eies av Keycloak. config-cli
   får 403 når det leser secret eller oppdaterer roller, også som admin. `sanitize` fjerner dem.
7. **Foreldreløse `authenticatorConfig`** som ingen execution bruker, avvises av config-cli. `sanitize`
   fjerner dem med en advarsel, og drift-jobben rapporterer dem helt til de er ryddet i Keycloak.
   I testmiljøet gjelder det `test-bajs`.
8. **Realm-navnet varierer** mellom miljøer, for eksempel `matrikkelen-test`. `master.json` bruker
   `$(env:REALM_NAME)`, som må settes ved bootstrap.

## Krav i bygning-apps

- **SKIPJob `matrikkel-keycloak-config`** per namespace. Kjøres ved ny versjon.
  - Env: `CONFIG_ENV=<miljø>` og `KEYCLOAK_URL=http://matrikkel-keycloak:8080/auth`.
  - `KEYCLOAK_CLIENTSECRET` og `$(env:...)`-secrets, for eksempel `SCHEDULE_JOB_CLIENT_SECRET`,
    fra ExternalSecret.
  - Eventuelle `IMPORT_MANAGED_*`-overstyringer for fase 2.
  - AccessPolicy ut mot Keycloak-servicen.
- **SKIPJob `matrikkel-keycloak-config-drift`** med cron (nattlig), samme env og argument
  `/config/<miljø>/realm.json`.
- **ExternalSecret** fra GSM for apply-klienten, injiserte klient-secrets og vault-secrets.
- **Keycloak:** `KC_VAULT=file` og `KC_VAULT_DIR`, med vault-secreten montert som filer.
