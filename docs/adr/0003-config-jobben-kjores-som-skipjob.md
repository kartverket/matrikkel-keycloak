# 0003: Config-jobben kjøres som SKIPJob, ikke GitHub Action

- **Status:** Akseptert
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

keycloak-config-cli må kjøres et sted som når Keycloaks Admin API og har en secret for å logge inn. De to
realistiske alternativene var en GitHub Action rett mot Keycloak, eller en jobb i Kubernetes-namespacet til
Keycloak.

## Beslutning

Configen bygges til imaget `ghcr.io/kartverket/matrikkel-keycloak-config` i `main.yml` og kjøres som SKIPJob
uten cron i hvert namespace. Den rulles ut via bygning-apps (`apps-repo-deploy`) på samme måte og samtidig som
Keycloak-imaget.

## Alternativer vurdert

| | SKIPJob (valgt) | GitHub Action rett mot Keycloak |
|---|---|---|
| Versjonering | Config-imaget versjoneres og promoteres sammen med Keycloak | Kjører siste versjon av configen på main |
| Secrets | GSM → ExternalSecret, som for andre apper | Apply-secreten må ligge i GitHub |
| Nettverk | Når Keycloak internt (`http://keycloak:8080/auth`) | Admin-API-et må kunne nås fra runneren |
| Ulempe | Et nytt image for hver endring | Avviker fra hvordan vi ellers ruller ut |

## Konsekvenser

- Hver config-endring gir et nytt image, og samme versjon kan promoteres dev → prodtest → prod.
- Secrets ligger bare i GSM.
- Jobben kjører bare ved deploy. En endring i GUI blir stående til neste deploy. Drift-deteksjon må løses separat.
- bygning-apps trenger SKIPJob, ExternalSecret, versjonsfil og inngående tilgang i Keycloak per miljø.
- `suspend` i SKIPJob-en brukes til å holde jobben av til et miljø er klart.

## Referanser

- `.github/workflows/main.yml`
- bygning-apps: `templates/applications/matrikkel-keycloak-realm-config.libsonnet`
