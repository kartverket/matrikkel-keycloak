
# Matrikkel Keycloak

Dette repoet inneholder matrikkelen sin keycloak installasjon. Den består av et docker image som er basert på keycloak, i tillegg til et sett
med extensions (providers) og custom themes som brukes for å utvide og tilpasse matrikkelen sin Keycloak installasjon.

Tanken er at keycloak og extensions er såpass tett koblet at disse bygges og releases sammen som en del av docker bygget. 
Extensions biblioteket har derfor ikke en egen versjonering eller publisering, men den nåværende versjonen av biblioteket blir pakket med og distribuert
med det ferdig bygde docker imaget.

[`Dockerfile`](Dockerfile) inneholder definisjonen av docker imaget som bygges og kjøres på SKIP. Det er basert på et standard keycloak image,
men bygges med et par ulike custom providers.

Når dette docker imaget bygges, blir også de custom extensions bygget i tillegg til en annen tredjeparts provider: [keycloak metrics spi](https://github.com/aerogear/keycloak-metrics-spi)

## Bygging og kjøring lokalt

For å teste ting lokalt er det enkleste å bygge med docker compose.
Som standard bygges Keycloak lokalt med `KC_DB=dev-mem` som betyr at man benytter en in-memory database. Hvis man ønsker å benytte en annen database lokalt
kan man endre `KC_DB` variabelen i [docker-compose.yml](docker-compose.yml) til ønsket database. 
Se også keycloak sin egen [dokumentasjon](https://www.keycloak.org/server/all-config#category-database).

```shell
docker compose build
```

For å kjøre opp keycloak lokalt kan man benytte:

```shell
docker compose up
```

Som default benytter man konfigurasjon fra filen [local.env](local.env). Disse kan endres hvis man ønsker å teste andre konfigurasjoner.

## Release

Det bygges automatisk en ny release ved push til `main`. Under bygging skjer følgende:

1. Ny versjon beregnes automatisk basert på forrige tag og de nyeste commit-meldingene.
  Det benyttes en action [mathieudutour/github-tag-action](https://github.com/mathieudutour/github-tag-action) for dette. Se dens dokumentasjon for hvordan man kan overstyre/tilpasse versjonsnummer. 
2. Commit-en som blir bygget blir tag-et med med versjonnummeret fra 1. og et docker image med samme tag blir blir pushet.  
3. Docker image blir deployet til dev (via ArgoCD) og dette bygget kan videre promoteres til test og produksjon ved hjelp [promote app](https://github.com/kartverket/heimdall-apps/actions/workflows/promote-app.yml)

## Bygging og extensions

Extensions blir bygget når man bygger keycloak, men hvis man ønsker kan man bygge disse direkte med gradle:

```shell
cd extensions
./gradlew assemble
```

Resultatet er tre artifakter:

* `matrikkel-keycloak-extension-<version>.jar`: Artifakt med alle providers, men uten nødvendige 3. parts avhengigheter
* `matrikkel-keycloak-extension-<version>-all.jar`: Artifakt med alle providers, inkludert nødvendige 3. parts avhengigheter
* `matrikkel-keycloak-extension-<version>-themes.jar`: Artifakt med custom themes

De to siste artifaktene inkluderes i matrikkelen sin keycloak installasjon.

## Providere 

Følgende providere er tilgjengelig

### OauthEmailSenderProvider

En custom email provider for å kunne sende epost vha microsoft Exchange sitt Graph API med OAuth autentisering. 
Provideren benytter `com.azure:azure-identity` for oauth-autentiseringen og `com.microsoft.graph:microsoft-graph` for selve epost-utsendingen via Microsoft sitt Graph API.

Provideren er avhengig av at følgende miljøvariabler er satt for å fungere. Hvis en av disse mangler vil det kastes en feil under initialsiering av provideren.

```
KEYCLOAK_EMAIL_OAUTH_TENANT_ID
KEYCLOAK_EMAIL_OAUTH_CLIENT_ID
KEYCLOAK_EMAIL_OAUTH_SECRET_ID
KEYCLOAK_EMAIL_OAUTH_USER_ID
```

Denne provideren må eksplisitt aktiveres ved å legge inn følgende parametere til `kc.sh build`:

```shell
kc.sh --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider build
```

