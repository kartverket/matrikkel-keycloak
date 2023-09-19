# Matrikkel keycloak extensions

Dette biblioteket inneholder en rekke extensions (providers) og custom themes som brukes for å utvide og tilpasse matrikkelen sin Keycloak installasjon.

## Bygg og distribusjon

For å bygge et artifakt som kan inkluderes i keycloak kjør følgende kommando:

```
./gradlew assemble
```

Resultatet er tre artifakter:

* `matrikkel-keycloak-extension-<version>.jar`: Artifakt med alle providers, men uten nødvendige 3. parts avhengigheter
* `matrikkel-keycloak-extension-<version>-all.jar`: Artifakt med alle providers, inkludert nødvendige 3. parts avhengigheter
* `matrikkel-keycloak-extension-<version>-themes.jar`: Artifakt med custom themes

De to siste artifaktene må så inkluderes inn i matrikkelen sin keycloak installasjon.

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

```
kc.sh --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider build
```
