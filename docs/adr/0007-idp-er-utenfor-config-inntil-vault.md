# 0007: Identity providers holdes utenfor configen inntil vault er på plass

- **Status:** Akseptert (midlertidig)
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

Realm-eksporten maskerer secrets (`**********`). De kan ikke ligge i Git. Lokal test viste at når
`clientSecret` mangler for IdP-ene `idporten-test`, `kartverket` og `microsoft`, tømmer config-cli feltet. Det
ville stoppet innlogging via ID-porten og Azure AD.

## Beslutning

`identityProviders` og `identityProviderMappers` holdes utenfor configen inntil videre. Med `no-delete` blir
IdP-ene stående urørt i Keycloak.

Målet er å ta dem inn med `${vault.<navn>}` som `clientSecret`. Det er Keycloaks innebygde, filbaserte vault, ikke
HashiCorp Vault. Verdiene ligger i GSM, synkes med ExternalSecret og monteres som filer i Keycloak-podden.

## Alternativer vurdert

- **`$(env:...)` i config-jobben:** secreten må da finnes i jobben, og den lagres som verdi i Keycloak-databasen.
  Med vault lagres bare referansen, og bare Keycloak-podden ser verdien.
- **Ta IdP-ene inn uten secret:** bryter innlogging.

## Konsekvenser

- IdP-konfigurasjonen forvaltes fortsatt manuelt i GUI til vault er på plass.
- Vault krever endringer i bygning-apps (montering av secrets i Keycloak) og nye GSM-secrets per miljø.
- Denne ADR-en erstattes når vault er innført.

## Referanser

- [Keycloak: Vault](https://www.keycloak.org/server/vault)
- `realm-config/README.md`, «Fra eksport til config: funn fra dev»
