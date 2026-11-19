# 0004: `no-delete` som standard, `full` slås på per ressurstype

- **Status:** Akseptert
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

keycloak-config-cli bruker som standard `full` forvaltning: ressurser som er fjernet fra configen, slettes fra
Keycloak. Miljøene har mye konfigurasjon som ennå ikke ligger i Git. En feil i configen kunne slettet klienter,
flows eller IdP-er og brutt innlogging.

Verifisert lokalt (se «Livsløp» i `realm-config/README.md`):
- Med `no-delete` blir fjernede ressurser liggende.
- Med `full` slettes bare ressurser som sto i configen ved forrige kjøring (remote state).
- Mangler en hel nøkkel, for eksempel `clients`, hoppes typen over.
- Endringer i GUI-et overskrives bare for felt som står i configen.

## Beslutning

Imaget setter alle `IMPORT_MANAGED_*` til `no-delete`. `full` slås på eksplisitt, én ressurstype om gangen,
når typen er komplett beskrevet i configen for alle miljøer.

## Alternativer vurdert

- **`full` fra start:** gir best samsvar med Git, men stor risiko for å slette ting som ikke er tatt inn ennå.
- **`IMPORT_REMOTESTATE_ENABLED=false`:** sletter alt som ikke står i configen, også ressurser som aldri har vært
  forvaltet. Bare aktuelt når configen er komplett.

## Konsekvenser

- Det er trygt å ta inn konfigurasjon gradvis.
- Ressurser som fjernes mens `no-delete` er aktivt, blir «glemt» av remote state og må ryddes bort manuelt.
- Unntak: config-cli sletter ubrukte authenticator-configer også med `no-delete`.

## Referanser

- [config-cli: Resource Management](https://github.com/adorsys/keycloak-config-cli/blob/main/docs/MANAGED.md)
