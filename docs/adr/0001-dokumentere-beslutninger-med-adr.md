# 0001: Dokumentere arkitekturbeslutninger med ADR

- **Status:** Akseptert
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

Overgangen til Keycloak-konfigurasjon som kode innebærer flere valg som ikke er åpenbare i ettertid: verktøy,
kjøremåte, slettestrategi og håndtering av secrets. Uten skriftlig begrunnelse er det vanskelig for nye på teamet
å forstå valgene, eller å vurdere når de bør endres.

## Beslutning

Arkitekturbeslutninger dokumenteres som ADR-er i `docs/adr/`, én markdown-fil per beslutning, i samme PR som
endringen. Malen ligger i `mal.md`.

## Alternativer vurdert

- **Confluence eller Jira:** lever adskilt fra koden og blir fort utdatert. Er heller ikke en del av review.
- **Bare README:** egner seg for hvordan ting fungerer, men ikke for hvorfor et valg ble tatt og hva som ble
  forkastet.

## Konsekvenser

- Beslutninger blir reviewet sammen med koden og versjonert i Git.
- Litt ekstra arbeid per større beslutning.
- README beskriver hvordan ting brukes. ADR-ene beskriver hvorfor.

## Referanser

- [Dokumenter arkitekturen din med ADR](https://www.bekk.christmas/post/2024/18/dokumenter-arkitekturen-din-med-adr)
