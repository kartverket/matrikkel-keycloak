# 0006: Gradvis innføring: første kjøring er no-op, verifisert lokalt

- **Status:** Akseptert
- **Dato:** 2026-11-17
- **Sak:** TB-1165

## Kontekst

En rå eksport av dev ga mange avvik da den ble applisert. To av dem ville vært alvorlige: IdP-secrets ble tømt,
og felt med standardverdier ble overskrevet. Uten testing kan en «uskyldig» config bryte innlogging i et miljø.

## Beslutning

- Første kjøring i et miljø bruker en minimal config som er verifisert no-op. For dev er det realm-navnet og
  `oauth2DevicePollingInterval`, uten den ville jobben satt feltet til standardverdien 5.
- Før en config rulles ut, testes den lokalt med `realm-config/scripts/lokal-test.sh <snapshot>`:
  1. Lager realmen fra et snapshot av miljøet.
  2. Kjører jobben.
  3. Viser en normalisert diff og sjekker at klient-secrets er uendret.
- Snapshots inneholder miljødata og er gitignorert.
- Rekkefølge: dev → prodtest → prod. Resten av configen tas inn i egne steg.

## Alternativer vurdert

- **Applisere hele den rensede eksporten direkte:** raskere, men vi hadde mistet IdP-secretene i dev.
- **Teste bare i dev:** gir ekte resultat, men feil rammer et delt miljø.

## Konsekvenser

- Hver endring som skal til et miljø, har en dokumentert diff før utrulling.
- Eksporten maskerer klient-secrets. Skriptet gir derfor hver klient en unik secret før jobben og sammenligner
  etterpå.
- Lokale avvik (`userinfo.token.claim`) skyldes Keycloaks egen import og må ikke forveksles med reelle endringer.

## Referanser

- `realm-config/scripts/lokal-test.sh`, `realm-config/scripts/diff.py`
- `realm-config/README.md`, «Fra eksport til config: funn fra dev»
