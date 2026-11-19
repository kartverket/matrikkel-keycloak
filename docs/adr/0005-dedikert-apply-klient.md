# 0005: Dedikert apply-klient med minst mulig rettigheter

- **Status:** Akseptert
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

Config-jobben må logge inn i Keycloak for å endre realmen via Admin REST API. Den kjører uten et menneske til
stede, så den kan ikke bruke personlige kontoer med Azure AD eller MFA.

## Beslutning

Klienten `matrikkel-keycloak-config` opprettes i master-realmen med `client_credentials`. Den får bare roller
på `<realm>-realm` (for eksempel `manage-realm`, `manage-clients`, `manage-identity-providers` og `manage-users`).

- Secreten er en tilfeldig streng i GSM (`kc-<miljø>-realm-config-client-secret`). Jobben får den via
  ExternalSecret.
- Klienten opprettes manuelt én gang per miljø (bootstrap med `realm-config/master/master.json`), fordi den
  ikke kan opprette seg selv.

## Alternativer vurdert

- **Master-admin (brukernavn/passord):** full tilgang til alle realms. Personlige eller delte passord i en jobb.
- **Klient i `matrikkelen-*`-realmen:** mulig, men realm-admin-rollene som trengs, er enklest å avgrense fra
  master. Det gir også ett mønster for alle miljøer.
- **Rollen `admin` i master:** enkelt, men gir tilgang til alt, også å opprette og slette realms.

## Konsekvenser

- Lekker secreten, kan noen endre alt i realmen, men ikke i master eller andre realms.
- Endringer fra jobben kan skilles fra personlige endringer i admin events.
- Rotasjon: ny verdi i GSM og ny bootstrap.
- Dette er grunnlaget for at personlige kontoer senere kan bli read-only for konfigurasjon.
- Status: opprettet i dev 2026-11-17. Verifisert at klienten kan lese `matrikkelen-test` og ikke master.

## Referanser

- `realm-config/master/master.json`
- `realm-config/README.md`, «Apply-klienten» og «Bootstrap»
