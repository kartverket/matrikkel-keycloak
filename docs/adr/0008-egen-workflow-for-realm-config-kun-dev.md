# 0008: Egen workflow for realm-config, foreløpig bare dev

- **Status:** Akseptert
- **Dato:** 2026-11-18
- **Sak:** TB-1165
- **Endrer:** [0003](0003-config-jobben-kjores-som-skipjob.md), delen om at config-imaget bygges i `main.yml`

## Kontekst

ADR 0003 la byggingen av config-imaget inn i `main.yml`, sammen med Keycloak. `main.yml` ruller ut til alle
miljøer, men configen skal i første omgang bare til dev. prodtest og prod venter til configen er komplett og
verifisert. Vi vil heller ikke endre `main.yml`, som ruller ut Keycloak til prod, før det er nødvendig.

## Beslutning

Alt som gjelder config-imaget, ligger i en egen workflow, `.github/workflows/realm-config.yml`. `main.yml` er
uendret.

- Den kjører ved endringer i `realm-config/**`.
- Den ruller bare ut til dev, bak `vars.REALM_CONFIG_DEPLOY`.
- Den kan startes manuelt.

SKIPJob, GSM og ExternalSecret fra ADR 0003 gjelder fortsatt.

## Alternativer vurdert

- **Bygge i `main.yml`:** Keycloak og config promoteres alltid sammen, men det krever endringer i workflowen som
  ruller ut til prod, og configen ville fulgt med til alle miljøer.
- **`main.yml` med stifilter** (for eksempel `dorny/paths-filter`): én deploykjede, men mer kompleks logikk
  i en workflow som også gjelder prod.

## Konsekvenser

- Config-endringer går til dev uten at deploykjeden for Keycloak endres.
- `main.yml` ignorerer ikke `realm-config/**`. En config-endring på main bygger og ruller derfor også ut Keycloak
  på nytt, slik andre endringer gjør i dag.
- Ved en Keycloak-oppgradering endres både `Dockerfile` (rot) og `realm-config/Dockerfile`, så begge workflowene
  kjører parallelt. Config-jobben kan da kjøre mot den gamle Keycloak-versjonen. Er det et problem, kjøres
  `realm-config.yml` manuelt etter at Keycloak er ute.
- Det samme gjelder config som bruker en ny egen provider: Keycloak med provideren må ut først.
- Config-endringer tagges ikke i Git, slik `main.yml` gjør.
- Når prodtest og prod skal inn, må det besluttes om denne workflowen utvides med samme godkjenningskjede,
  eller om configen flyttes tilbake i `main.yml`. Det skrives i en ny ADR.

## Referanser

- `.github/workflows/realm-config.yml`
- `realm-config/README.md`, «Pipeline»
