# Architecture Decision Records (ADR)

Korte notater om arkitekturbeslutninger i matrikkel-keycloak: hva som ble valgt, hvilke alternativer som ble
vurdert, og hvorfor. Én markdown-fil per beslutning, nummerert fortløpende.

| # | Beslutning | Status |
|---|---|---|
| [0001](0001-dokumentere-beslutninger-med-adr.md) | Dokumentere arkitekturbeslutninger med ADR | Akseptert |
| [0002](0002-keycloak-config-cli-som-iac-verktoy.md) | keycloak-config-cli som verktøy for realm-konfigurasjon som kode | Akseptert |
| [0003](0003-config-jobben-kjores-som-skipjob.md) | Config-jobben kjøres som SKIPJob, ikke GitHub Action | Akseptert |
| [0004](0004-no-delete-som-standard.md) | `no-delete` som standard, `full` slås på per ressurstype | Akseptert |
| [0005](0005-dedikert-apply-klient.md) | Dedikert apply-klient med minst mulig rettigheter | Akseptert |
| [0006](0006-gradvis-innforing-med-no-op-forst.md) | Gradvis innføring: første kjøring er no-op, verifisert lokalt | Akseptert |
| [0007](0007-idp-er-utenfor-config-inntil-vault.md) | Identity providers holdes utenfor configen inntil vault er på plass | Akseptert (midlertidig) |
| [0008](0008-egen-workflow-for-realm-config-kun-dev.md) | Egen workflow for realm-config, foreløpig bare dev | Akseptert |
| [0009](0009-config-per-miljo-base-og-resources.md) | Config per miljø med base og resources, én fil per ressurstype | Akseptert |

## Ny ADR

1. Kopier [`mal.md`](mal.md) til `NNNN-kort-tittel.md` med neste ledige nummer.
2. Fyll ut og legg den til i tabellen over.
3. Ta den med i samme PR som endringen den beskriver.

En akseptert ADR endres ikke i etterkant. Endres beslutningen, skriv en ny ADR og sett den gamle til
«Erstattet av NNNN».

Se også [Dokumenter arkitekturen din med ADR](https://www.bekk.christmas/post/2024/18/dokumenter-arkitekturen-din-med-adr).
