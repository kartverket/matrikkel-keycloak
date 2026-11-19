# realm-config – Keycloak-konfigurasjon som kode

Realm-konfigurasjonen appliseres med [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli)
som en SKIPJob. Foreløpig inneholder `envs/dev/realm.json` bare realm-navnet og dev-verdien for
`oauth2DevicePollingInterval`. Jobben logger inn og kjører, men endrer ingenting (verifisert lokalt mot et
snapshot av dev). Uten `oauth2DevicePollingInterval` ville jobben satt feltet tilbake til standardverdien 5.
Hele dev-configen tas inn i neste steg (se «Fra eksport til config» under).

Begrunnelsen for valgene står i [`docs/adr/`](../docs/adr/README.md).

```
realm-config/
├── Dockerfile           keycloak-config-cli (bygget mot vår Keycloak-versjon) + envs/ og master/
├── envs/<miljø>/*.json  konfigurasjon per miljø, i samme format som en realm-eksport
└── master/master.json   bootstrap av apply-klienten i master-realmen (kjøres manuelt)
```

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

## Fra eksport til config: funn fra dev

En rå realm-eksport (Partial export) kan ikke appliseres direkte. Testet lokalt ved å importere en eksport fra
dev med Keycloaks egen import og deretter kjøre config-jobben med en renset versjon av eksporten. Den rensede
versjonen er ikke committet ennå.

Må fjernes eller endres i eksporten før den kan brukes:

| Hva | Hvorfor |
|---|---|
| `id`, `containerId`, `createdTimestamp`, `de.adorsys.*`-attributter, `keycloakVersion` | Miljøspesifikt eller generert. |
| Maskerte secrets (`**********`) | Ellers blir secreten satt til nettopp denne strengen. |
| `components."org.keycloak.keys.KeyProvider"` | Realm-nøkler skal ikke i Git. |
| Klientene `broker` og `realm-management`, inkludert rollene deres | Innebygde klienter (`realm_client=true`). Keycloak 26 avviser endringer med 403, også fra admin. |
| Komponenten «Default Roles Disabled» (`providerId: default-roles`) | Provideren finnes ikke i vårt image. Rester i dev som bør ryddes bort der. |
| Authenticator-configer som ingen execution bruker (`test-bajs`) | config-cli avviser dem. |
| `authenticationFlowBindingOverrides` med flow-ID | Byttes til flow-alias, siden ID-ene er ulike i hvert miljø. |
| `attributes."client.secret.creation.time"` på klienter | Metadata om klient-secreten, som eies av Keycloak. Ellers setter jobben tilbake tidspunktet. |
| `identityProviders` og `identityProviderMappers` | Midlertidig. Se IdP-secrets under. |

Kan ikke opprettes, bare oppdateres: de innebygde flowene `Browser - Conditional OTP` og
`First broker login - Conditional OTP`. De finnes i dev fra en eldre Keycloak-versjon, men ikke i nye realms.

Config-jobben er **nesten** no-op mot dev. Diff før og etter at jobben kjørte (`scripts/lokal-test.sh`):

| Endring | Konsekvens i dev | Status |
|---|---|---|
| `clientSecret` på IdP-ene (`idporten-test`, `kartverket`, `microsoft`) ble tom | Innlogging via ID-porten og Azure AD ville sluttet å virke. Eksporten maskerer secrets, og config-cli tømte feltet når det manglet. | **Løst midlertidig:** IdP-er og mappere er tatt ut av configen. Med `no-delete` står de urørt i Keycloak. Tas inn igjen med `${vault.x}` (GSM → ExternalSecret → fil i Keycloak-podden). |
| Scopet `service_account` legges til klienter som har service account (4 stk.) | Keycloak 26 legger det til når klienten oppdateres. Tokenene får claimene `client_id`, `clientHost` og `clientAddress`, men de fire klientene har allerede de samme claimene som egne mappere, så tokenene blir like. | **Akseptert:** lagt til i `defaultClientScopes` i configen, slik at den stemmer med Keycloak. Endringen skjer i dev ved første kjøring. |
| Ubrukt authenticator-config slettes | config-cli rydder bort ubrukte configer, også med `no-delete`. | Ufarlig. |

Klient-secrets beholdes. Eksporten maskerer dem, så diffen ser dem ikke. Derfor gir skriptet hver
konfidensiell klient en unik secret før jobben, og sammenligner dem etterpå (steg 7). Klienter uten `secret`
i configen beholder secreten sin.

Lokale avvik på `userinfo.token.claim` skyldes Keycloaks egen import og oppstår ikke i dev.

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
