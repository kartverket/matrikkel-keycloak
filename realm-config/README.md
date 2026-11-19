# realm-config – Keycloak-konfigurasjon som kode

Realm-konfigurasjonen appliseres med [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli)
som en SKIPJob. `envs/dev/` inneholder hele dev-configen unntatt identity providers, delt opp i én fil per
ressurstype. Alle typer kjører med `no-delete`. Lokalt mot et snapshot av dev er jobben no-op, bortsett fra
de forventede endringene under «Fra eksport til config».

Begrunnelsen for valgene står i [`docs/adr/`](../docs/adr/README.md).

```
realm-config/
├── Dockerfile                    keycloak-config-cli (bygget mot vår Keycloak-versjon) + envs/ og master/
├── envs/<miljø>/
│   ├── base/realm.json           realm-innstillinger for miljøet
│   └── resources/NN-<type>.json  én fil per ressurstype (se under)
├── master/master.json            bootstrap av apply-klienten i master-realmen (kjøres manuelt)
└── scripts/                      split.py (migrering), check.py (CI-sjekk), lokal-test.sh og diff.py (lokal no-op-test)
```

Hvert miljø har sin egen komplette config. Ingenting deles mellom miljøene. Foreløpig finnes bare `envs/dev/`.

### Filer per miljø

| Fil | Innhold | `IMPORT_MANAGED_*` |
|---|---|---|
| `resources/20-client-scopes.json` | `clientScopes`, default (optional) client scopes for realmen | `CLIENTSCOPE` |
| `resources/30-authentication-flows.json` | egne flows (`builtIn=false`), `authenticatorConfig` som de bruker, flow-bindinger | `AUTHENTICATIONFLOW` |
| `resources/35-required-actions.json` | `requiredActions` | `REQUIREDACTION` |
| `resources/40-clients.json` | egne klienter, med protocol mappers og flow-overrides (alias) | `CLIENT`, `CLIENTAUTHORIZATION*` |
| `resources/50-roles.json` | realm-roller og klientroller for egne klienter | `ROLE` |
| `resources/60-scope-mappings.json` | `scopeMappings`, `clientScopeMappings` | `SCOPEMAPPING`, `CLIENTSCOPEMAPPING` |
| `resources/70-service-accounts.json` | service-account-brukere med rolle- og gruppetildelinger | (ingen, brukere har ikke state) |
| `resources/75-groups.json` | grupper (lages bare når miljøet har grupper, dev har ingen) | `GROUP` |
| `resources/80-components.json` | komponenter unntatt realm-nøkler og user profile | `COMPONENT`, `SUBCOMPONENT` |
| `resources/85-user-profile.json` | `userProfile` | (ingen) |
| (senere) `resources/45-identity-providers.json` | `identityProviders`, `identityProviderMappers` | `IDENTITYPROVIDER*` |
| `base/realm.json` | realm-innstillinger og -attributter | (ingen) |

Regler:

- **Én ressurstype i nøyaktig én fil.** config-cli lagrer remote state per type, og state-lista erstattes for
  hver fil. To filer med samme type ville slettet hverandres ressurser i `full`-modus.
- **Prefiksene (20, 30, 35 …) styrer rekkefølgen.** Filene i `resources/` importeres alfabetisk, så client
  scopes og flows finnes før klientene som bruker dem, og klientene før klientrollene. Hullene gir plass til
  nye typer uten å endre navn på de andre.
- **`base/` importeres etter `resources/`.** Rekkefølgen står i `IMPORT_FILES_LOCATIONS` i `Dockerfile`. Hver
  fil gjør en realm-oppdatering, og Keycloak setter for eksempel `oauth2DevicePollingInterval` tilbake til 5
  når feltet mangler. Siste fil bestemmer verdien.
- **Git er fasit.** Endringer gjøres i filene her og går gjennom PR. Vi eksporterer ikke fra Keycloak og
  genererer på nytt. Opprydding (testrester o.l.) gjøres også som endringer i filene.
- **Aldri secrets, realm-nøkler, ID-er eller personlige brukere i filene.** Se «Hva som ikke skal i Git» under.

### Migrering av et miljø (én gang per miljø)

`envs/dev/` er generert fra en eksport av dev 19.11.2026 og er slik dev var da. `split.py` brukes bare til å
ta inn et nytt miljø (prodtest, prod) første gang:

```bash
# Fra rota av repoet. Eksporten tas med Partial export (grupper, roller og klienter) i Admin GUI.
PYTHONIOENCODING=utf-8 python realm-config/scripts/split.py prodtest.snapshot.json realm-config/envs/prodtest
```

Skriptet skriver ut `OBS`-linjer for alt det utelater som ikke er rutine (ubrukte authenticator-configer,
ukjente komponenter og IdP-er).

## Hvorfor SKIPJob og ikke GitHub Action

| | SKIPJob (valgt) | GitHub Action rett mot Keycloak |
|---|---|---|
| Versjonering | Config-imaget versjoneres og rulles ut sammen med Keycloak | Kjører siste versjon av configen på main |
| Secrets | GSM → ExternalSecret, som for andre apper | Apply-secreten må ligge i GitHub |
| Nettverk | Når Keycloak internt (`http://keycloak:8080/auth`) | Admin-API-et må kunne nås fra runneren |
| Ulempe | Et nytt image for hver endring | Avviker fra hvordan vi ellers ruller ut |

## Pipeline

Egen workflow, `.github/workflows/realm-config.yml`, foreløpig bare for dev (se ADR 0008):

- Kjører ved endringer i `realm-config/**`, men ikke ved endringer i `*.md` eller `scripts/`. `main.yml` er uendret
  og bygger ikke config-imaget.
- PR: bygger imaget uten å pushe.
- main: pusher `ghcr.io/kartverket/matrikkel-keycloak-config:<versjon>` og oppdaterer
  `matrikkel-keycloak-realm-config` i bygning-apps for dev. Deploy-steget er av til repo-variabelen
  `REALM_CONFIG_DEPLOY` settes til `true`.
- Kan kjøres manuelt (`workflow_dispatch`), for eksempel for å kjøre configen på nytt etter en Keycloak-utrulling.

config-cli bygges fra kildekode, fordi ferdigbygde images ligger etter vår Keycloak-versjon og avviser
nye felt. `KEYCLOAK_VERSION` i `Dockerfile` må følge Keycloak-versjonen i `extensions/build.gradle`.

## Livsløp

Verifisert lokalt mot vårt Keycloak-image (26.8.2) og config-cli 6.5.1.

| Situasjon | Resultat |
|---|---|
| Ressurs fjernet fra configen, `no-delete` (standard i imaget) | Blir liggende i Keycloak. |
| Ressurs fjernet fra configen, `IMPORT_MANAGED_<TYPE>=full` | Slettes, men bare hvis den sto i configen ved forrige kjøring (remote state). Ressurser som aldri har stått i configen, for eksempel innebygde klienter og ting laget i GUI, slettes ikke. |
| Ressurs fjernet mens `no-delete` var aktivt, og `full` slått på senere | Slettes **ikke**. Remote state har allerede «glemt» den. Den må ryddes bort manuelt. |
| Hele nøkkelen mangler, for eksempel ingen `clients` i filen | Typen hoppes over. Ingenting endres eller slettes, heller ikke med `full`. |
| Felt endret i Admin GUI, og feltet står i configen | Overskrives med verdien fra Git neste gang jobben kjører. |
| Felt eller ressurs endret i Admin GUI, men står ikke i configen | Blir stående. config-cli rører bare det som står i configen. |

Jobben kjører bare når en ny versjon rulles ut. En endring i GUI står derfor til neste deploy, og ingenting
varsler om avviket.

config-cli bruker `full` som standard. Imaget setter derfor alle `IMPORT_MANAGED_*` til `no-delete`, og
`full` slås på eksplisitt per ressurstype.

## Hva som ikke skal i Git

Repoet er offentlig. Gjelder både migrering og alle senere endringer i `envs/`:

| Aldri i Git | Slik løses det i stedet |
|---|---|
| Klient-secrets (`secret` på klienter) | Eies av Keycloak, eller `$(env:...)` fra GSM via ExternalSecret i config-jobben. |
| IdP- og SMTP-secrets (`clientSecret`, `smtpServer.password`) | `${vault.x}`: GSM → ExternalSecret → fil i Keycloak-podden. |
| Realm-nøkler (`org.keycloak.keys.KeyProvider`) | Eies av Keycloak i hvert miljø. |
| Personlige brukere og persondata (navn, e-post, fødselsnummer) | Brukere administreres i Keycloak. Bare service accounts i `70-service-accounts.json`. |
| ID-er og tidsstempler (`id`, `containerId`, `createdTimestamp` …) | Ressurser refereres med navn/alias. |
| config-cli-state (`de.adorsys.keycloak.config.*`) | Skrives av config-cli i Keycloak. |

Ved migrering fjerner `split.py` dette automatisk (konstantene øverst i skriptet). Ved senere endringer
stopper `scripts/check.py` PR-en (første jobb i `realm-config.yml`). Kjør den lokalt før du pusher:

```bash
PYTHONIOENCODING=utf-8 python realm-config/scripts/check.py
```

Sjekken fanger secrets i klartekst (alt annet enn `$(env:...)` og `${vault.x}`), maskerte secrets, realm-nøkler,
ID-er, config-cli-state, personlige brukere, e-postadresser (unntatt `ALLOWED_EMAILS`) og 11-sifrede tall.
Den fanger ikke alt, så PR-review gjelder fortsatt.

## Fra eksport til config: funn fra dev

En rå realm-eksport (Partial export) kan ikke appliseres direkte. `scripts/split.py` gjør dette ved migrering:

| Hva | Hvorfor |
|---|---|
| `id`, `internalId`, `containerId`, `createdTimestamp`, `notBefore`, `keycloakVersion`, `defaultRole` | Miljøspesifikt eller generert. `defaultRole` heter `default-roles-<realm-id>`. |
| Realm-attributtene `de.adorsys.keycloak.config.*` og `*.lastExecutionTime` | State fra config-cli og kjøretidsdata. |
| `secret` og `client.secret.*`-attributter på klienter | Eksporten maskerer secreten (`**********`). Klient-secrets eies av Keycloak. |
| `components."org.keycloak.keys.KeyProvider"` | Realm-nøkler skal ikke i Git. |
| Innebygde klienter (`account`, `account-console`, `admin-cli`, `broker`, `realm-management`, `security-admin-console`) og rollene deres | Keycloak lager dem selv. `broker` og `realm-management` avvises med 403, også fra admin. |
| Innebygde flows (`builtIn=true`) | Keycloak lager dem selv. `Browser - Conditional OTP` og `First broker login - Conditional OTP` finnes bare i eldre realms og kan ikke opprettes. |
| Authenticator-configer som ingen egen flow bruker (`test-bajs` m.fl.) | config-cli avviser dem. |
| Komponenten «Default Roles Disabled» (`providerId: default-roles`) | Provideren finnes ikke i imaget vårt. Den ligger igjen i dev og bør ryddes bort der. |
| Brukere, unntatt service accounts | Ingen personlige brukere i Git. Service accounts tas med for rolletildelingene. |
| `identityProviders` og `identityProviderMappers` | Midlertidig. Se under. |
| `localizationTexts` | Tomme i dev. |

Endret: flow-ID-er i `authenticationFlowBindingOverrides` byttes til alias, og user profile flyttes fra
komponenten `kc.user.profile.config` til `userProfile`.

Diff mellom dev-eksporten og realmen etter config-jobben (`scripts/lokal-test.sh` + `diff.py`):

| Endring | Konsekvens i dev | Status |
|---|---|---|
| `clientSecret` på IdP-ene (`idporten-test`, `kartverket`, `microsoft`) ble tom | Innlogging via ID-porten og Azure AD ville sluttet å virke. Eksporten maskerer secrets, og config-cli tømmer feltet når det mangler. | **Midlertidig løst:** IdP-er og mappere er ikke med i configen. Med `no-delete` står de urørt. Tas inn med `${vault.x}` (GSM → ExternalSecret → fil i Keycloak-podden). |
| Authenticator-configen `test-bajs` slettes | config-cli sletter ubrukte configer når `authenticationFlows` er med, også med `no-delete`. | Forventet og ufarlig (testrest). |
| Scopet `service_account` legges til `c10136`, `jenkins`, `matrikkel-test-eksterne` og `releasetest` | Keycloak 26 legger det til ved enhver oppdatering av en klient med service account, også ved lagring i GUI. Tokenene får i tillegg claimen `client_id`. `clientHost` og `clientAddress` finnes fra før som egne mappere. | Configen følger eksporten (uten scopet). config-cli hopper over klienter som er uendret, så dev forventes uendret. Lokalt skjer det fordi Keycloaks egen import gir små avvik. Når klientene endres senere, kommer scopet uansett. |

Klient-secrets beholdes. Eksporten maskerer dem, så diffen ser dem ikke. Derfor gir skriptet hver
konfidensiell klient en unik secret før jobben, og sammenligner dem etterpå. Klienter uten `secret`
i configen beholder secreten sin.

Lokale avvik på `userinfo.token.claim`, `post.logout.redirect.uris`, `client.secret.creation.time` og
`browserSecurityHeaders` skyldes Keycloaks egen import og oppstår ikke i dev. Sammenlign derfor eksporten fra
miljøet med `local/etter.json`, ikke `local/for.json`:

```bash
PYTHONIOENCODING=utf-8 python realm-config/scripts/diff.py dev-etter.snapshot.json realm-config/local/etter.json
```

### Til vurdering før configen er ferdig vasket

Repoet er offentlig. Eksporten inneholder ingen persondata eller secrets, men følgende bør vurderes:

- **Interne vertsnavn** i redirect-URI-er og SMTP (`*.statkart.no`, interne cloud-URL-er). Ikke hemmelige, men
  avslører intern infrastruktur.
- **Azure tenant-ID og IdP-klient-ID-er.** Ikke hemmelige, men kan flyttes til miljøvariabler (`$(env:...)`).
- **Testrester i dev:** klientene `taaosfjaof`, `testgruppe`, `jwt-test-client`, `person-api` (localhost),
  flowene `Copy of registration`, `matrikkel-direct-grant-copy`, `matrikkel-browser-alt`. Bør slettes i dev og
  fjernes fra configen, ikke tas med til prodtest og prod.
- `webAuthnPolicyRpId: "admmatr"` ser feil ut.
- Attributtet `loclae` (skrivefeil) på service-accounten til `matrikkel-test-eksterne`.
## Apply-klienten `matrikkel-keycloak-config`

Config-jobben trenger en innlogging i Keycloak for å endre realmen via Admin REST API. Den bruker en egen
klient i master-realmen, ikke en personlig konto og ikke master-admin.

| | |
|---|---|
| Hvor | Master-realmen, opprettet av `master/master.json` (bootstrap) |
| Innlogging | `client_credentials` med client secret. Ingen nettleserflyt eller direct grant. |
| Rettigheter | Bare roller på `<realm>-realm`-klienten (`manage-realm`, `manage-clients`, `manage-identity-providers`, `manage-users` osv.). Ingen rettigheter i master eller andre realms, og ingen `create-realm`. |
| Secret | Tilfeldig streng (`openssl rand -hex 32`) i GSM som `kc-<miljø>-realm-config-client-secret`. Bootstrap setter den på klienten, og jobben får den som `KEYCLOAK_CLIENTSECRET` via ExternalSecret. |
| Risiko | Den som har secreten, kan endre alt i realmen. Derfor ligger den bare i GSM. |
| Rotasjon | Ny verdi i GSM, kjør bootstrap på nytt, og restart ExternalSecret-synken. |
| Full scope | Av (`fullScopeAllowed: false`). Tokenet får bare rollene i `clientScopeMappings`, og Admin API krever at rollen både er tildelt og innenfor scope. En ny rolle må derfor legges inn både i `clientRoles` og i `clientScopeMappings` i `master.json`, ellers svarer API-et 403. |
| Client scope `kc-admin-api` | Må være default scope. Siden 18.11.2026 sjekker Keycloak audience i admin-tokens (`KC_SPI_ADMIN__AUDIENCE_CHECK_ENABLED` er ikke lenger `false`). Scopet legger `<frontend-url>/admin/realms/master` i `aud`. Uten det svarer Admin API 401 på alt, og jobben logger «Server info check failed with 401» til den gir opp. |

Hvorfor en egen klient:
- Jobben kan kjøre uten personlige kontoer, passord eller MFA.
- Rettighetene er begrenset til realmen jobben forvalter. Den kan ikke endre master eller opprette realms.
- Endringer i admin events og logger kan spores til jobben.
- Det gir grunnlaget for at personlige kontoer senere kan bli read-only for konfigurasjon.

Status: opprettet i dev 2026-11-17.

## Bootstrap av apply-klienten (én gang per miljø)

Krever en lokal admin-bruker i master-realmen med passord. Innlogging via Azure AD eller MFA fungerer ikke med
`password`-grant. Bootstrap rører bare master-realmen og legger til én klient. Det er ingen restart eller nedetid,
og `no-delete` gjelder. Rull tilbake ved å slette klienten.

1. Lag secreten i GSM: Secret Manager i miljøets prosjekt → Create secret →
   `kc-<miljø>-realm-config-client-secret` med verdien fra `openssl rand -hex 32`.
2. Kjør bootstrap fra rota av repoet (Git Bash). Her for dev:
   ```shell
   docker build -q -t matrikkel-keycloak-config:local realm-config/
   SECRET=$(gcloud secrets versions access latest \
     --secret=kc-dev-realm-config-client-secret --project=mat-brukeradmin-dev-8cf7)
   read -p "Admin-bruker (master): " KC_USER; read -s -p "Passord: " KC_PASS; echo
   MSYS_NO_PATHCONV=1 docker run --rm \
     -e KEYCLOAK_URL=https://kc-test-matrikkel-admin.atkv3-dev.kartverket-intern.cloud/auth \
     -e KEYCLOAK_GRANTTYPE=password -e KEYCLOAK_CLIENTID=admin-cli \
     -e KEYCLOAK_USER="$KC_USER" -e KEYCLOAK_PASSWORD="$KC_PASS" \
     -e IMPORT_FILES_LOCATIONS='file:/master/*.json' \
     -e KEYCLOAK_CONFIG_CLIENT_SECRET="$SECRET" -e REALM_NAME=matrikkelen-test \
     matrikkel-keycloak-config:local 2>&1 | grep -E 'Using version|ERROR|ran in'
   unset SECRET KC_PASS
   ```
3. Verifiser at klienten kan lese realmen (forventet `200`) og ikke master (forventet `403`):
   ```shell
   KC=https://kc-test-matrikkel-admin.atkv3-dev.kartverket-intern.cloud/auth
   SECRET=$(gcloud secrets versions access latest --secret=kc-dev-realm-config-client-secret --project=mat-brukeradmin-dev-8cf7)
   TOKEN=$(curl -s -d client_id=matrikkel-keycloak-config -d client_secret="$SECRET" -d grant_type=client_credentials \
     $KC/realms/master/protocol/openid-connect/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
   curl -s -o /dev/null -w "Les realm: %{http_code}\n" -H "Authorization: Bearer $TOKEN" $KC/admin/realms/matrikkelen-test
   curl -s -o /dev/null -w "Les master: %{http_code}\n" -H "Authorization: Bearer $TOKEN" $KC/admin/realms/master/clients
   unset SECRET TOKEN
   ```

## Lokal kjøring

Kjør i Git Bash fra rota av repoet. Skriptet `scripts/lokal-test.sh` gjør hele løpet:
bygger config-imaget, starter Keycloak, oppretter realmen, kjører bootstrap og config-jobben, og viser en
diff av realmen før og etter jobben (`scripts/diff.py`). Krever Docker, curl og Python.

```shell
# 1. Bygg Keycloak-imaget én gang (og etter endringer i extensions/ eller Dockerfile).
#    Bygges fra git archive fordi gradlew feiler med CRLF på Windows. Bare committede endringer kommer med.
#    Base-imaget ligger på dhi.io og krever `docker login dhi.io`.
git -c core.autocrlf=false -c core.eol=lf archive HEAD | docker build --build-arg KC_DB=dev-mem -t matrikkel-keycloak:local -

# 2a. No-op-test: test mot en realm som er lik et miljø (se «Snapshot av et miljø» under)
bash realm-config/scripts/lokal-test.sh dev.snapshot.json

# 2b. Tom realm. Feiler med dagens dev-config («Unable to create built-in flows»), fordi den bruker eldre
#     innebygde flows som bare finnes i miljøene. Nyttig for config som ikke rører dem.
bash realm-config/scripts/lokal-test.sh

# Kjør bare config-jobben på nytt etter en endring i envs/ (Keycloak må kjøre)
docker build -q -t matrikkel-keycloak-config:local realm-config/
docker run --rm --network kc-local -e KEYCLOAK_URL=http://kc-local:8080/auth \
  -e KEYCLOAK_CLIENTSECRET=lokal -e CONFIG_ENV=dev matrikkel-keycloak-config:local

# Rydd opp
docker rm -f kc-local; docker network rm kc-local
```

Admin GUI: <http://localhost:8080/auth> (admin/admin). Eksporter og logger havner i `realm-config/local/`,
som er gitignorert. Legg til `-e IMPORT_MANAGED_CLIENT=full` (eller en annen type) for å teste sletting.

Diffen viser `-` (fjernet), `+` (lagt til) og `~` (endret, gammel -> ny verdi) per sti, for eksempel
`clients.clientId=jenkins.defaultClientScopes`. Med et snapshot av miljøet skal den vise 0 endringer før
configen rulles ut dit.

### Snapshot av et miljø

1. Admin GUI i miljøet → `matrikkelen-test` → Realm settings → Action → Partial export, med grupper, roller
   og klienter.
2. Fjern komponenter som ikke kan importeres i vårt image: `org.keycloak.keys.KeyProvider` og
   «Default Roles Disabled» (`providerId: default-roles`).
3. Lagre som `*.snapshot.json`. Filen inneholder miljødata og er gitignorert. Den skal **aldri** committes.

Realmen opprettes med Keycloaks egen import (`POST /admin/realms`), ikke med config-cli. Config-cli kan ikke
opprette eldre innebygde flows som finnes i miljøene, og i miljøene finnes realmen alltid fra før.

### Hvorfor kommandoene ser slik ut

| Hva | Hvorfor |
|---|---|
| `docker network create kc-local` og `--network kc-local` | Containere i Dockers standardnettverk finner ikke hverandre på navn. I et eget nettverk har Docker en innebygd DNS, slik at config-jobben når Keycloak på `http://kc-local:8080` (containernavnet). `localhost` inne i config-containeren er containeren selv. I SKIP gjør Kubernetes det samme med service-navnet i namespacet. |
| `-p 8080:8080` | Bare for at du skal nå Admin GUI fra nettleseren. Config-jobben går via nettverket. |
| `git ... archive HEAD \| docker build ... -` | På Windows sjekker git ut filer med CRLF, og da feiler `gradlew` i imaget. `archive` med `core.autocrlf=false` gir LF. Merk at bare committede endringer kommer med. |
| `--build-arg KC_DB=dev-mem` | Minnedatabase lokalt i stedet for Oracle. Alt forsvinner når containeren slettes. |
| `KC_HTTP_ENABLED`, `KC_HOSTNAME_STRICT=false` | Imaget starter med `start --optimized` (prod-modus), som ellers krever HTTPS og fast hostname. |
| `KEYCLOAK_EMAIL_OAUTH_*=x` | Imaget krever variablene for å starte. Verdiene brukes ikke lokalt. |
| `MSYS_NO_PATHCONV=1` | Git Bash gjør ellers om `/master/*.json` til en Windows-sti. |
| Bootstrap med `admin-cli` | Oppretter apply-klienten. Det gjøres én gang per miljø med en admin-konto, siden apply-klienten ikke kan opprette seg selv. |
