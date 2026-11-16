# 0002: keycloak-config-cli som verktøy for realm-konfigurasjon som kode

- **Status:** Akseptert
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

Realm-konfigurasjonen finnes i dag bare i databasen og endres manuelt i Admin GUI. Vi vil ha den som kode i Git,
slik at endringer blir sporbare og reviewet, miljøene holdes like, og konfigurasjonen kan gjenopprettes uten
manuelle klikk. Vi bruker egne providers (egne authenticators, mappers og `flow-return-oidc`), som verktøyet må
håndtere uten tap.

## Beslutning

Vi bruker [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli) (adorsys).

- Det bruker samme JSON-format som en realm-eksport. En eksport fra et miljø kan derfor renses og brukes direkte.
- Egne providers refereres med `providerId`/`authenticator`. De trenger ingen ekstra modellering.
- Det er idempotent og oppdaterer bare det som står i configen.

config-cli bygges fra kildekode mot vår Keycloak-versjon (`KEYCLOAK_VERSION` i `realm-config/Dockerfile`).
Ferdigbygde images ligger etter vår versjon og avviser nye felt i realm-representasjonen.

## Alternativer vurdert

- **Terraform (keycloak-provider):** egen HCL-modell per ressurstype. Egne providers og flows må modelleres
  manuelt, og en eksport kan ikke gjenbrukes. Krever i tillegg håndtering av Terraform-state. Ikke testet i PoC.
  Formatforskjellen og egne providers var nok til å velge config-cli.
- **Keycloaks innebygde import ved oppstart (`--import-realm`):** importerer bare realms som ikke finnes. Kan
  ikke oppdatere en eksisterende realm.
- **Egne skript mot Admin REST API:** full kontroll, men vi måtte selv bygge diff, idempotens og slettelogikk.

## Konsekvenser

- Ved oppgradering av Keycloak må `KEYCLOAK_VERSION` i `realm-config/Dockerfile` bumpes sammen med
  `extensions/build.gradle` og Dockerfile-digesten.
- En eksport må renses før bruk: ID-er, secrets, nøkler, innebygde klienter og ugyldige komponenter. Se
  `realm-config/README.md`.
- config-cli kan ikke opprette eldre innebygde flows. Lokal test bruker derfor Keycloaks egen import for å lage
  realmen.
- config-cli lagrer «remote state» som attributter på realmen (`de.adorsys.*`).

## Referanser

- [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli)
- `realm-config/README.md`
