# 0009: Config per miljø med base og resources, én fil per ressurstype

- **Status:** Akseptert
- **Dato:** 2026-11-19
- **Sak:** TB-1165

## Kontekst

Full forvaltning (`IMPORT_MANAGED_<TYPE>=full`) skal slås på én ressurstype om gangen. config-cli lagrer
remote state per ressurstype i realm-attributter, og hver importfil erstatter hele state-lista for typene
den inneholder. Hver fil gjør også en egen realm-oppdatering, så realm-felt som mangler i en fil, kan bli
satt tilbake til standardverdien.

## Beslutning

- Hvert miljø har sin egen komplette config i `envs/<miljø>/`. Ingenting deles mellom miljøene i denne fasen.
- `envs/<miljø>/base/realm.json` inneholder realm-innstillingene.
- `envs/<miljø>/resources/NN-<type>.json` inneholder én ressurstype per fil. Hver type ligger i nøyaktig én fil.
- Prefiksene styrer rekkefølgen innenfor `resources/`. `base/` importeres etter `resources/`
  (`IMPORT_FILES_LOCATIONS` i `realm-config/Dockerfile`).
- `envs/dev/` er generert én gang fra en eksport med `realm-config/scripts/split.py`, som også fjerner det som
  ikke skal i Git. Etter det er Git fasit, og endringer gjøres i filene. `split.py` brukes bare når et nytt
  miljø tas inn første gang.

## Alternativer vurdert

- **Én fil per miljø:** enkelt, men gir ikke noen enkel måte å slå på `full` per type på.
- **Felles base på tvers av miljøer:** mindre duplisering, men forskjeller mellom miljøene må da løses med
  `$(env:...)`, og en endring i base treffer alle miljøer samtidig. Vurderes igjen når prodtest og prod er inne.
- **Samme type i flere filer (overlay):** config-cli fletter ikke filer. Den siste filen ville overskrevet state
  fra den første, og i `full`-modus slettet ressursene derfra.

## Konsekvenser

- Det blir lett å se hvilken `IMPORT_MANAGED_*` en fil hører til, og å slå på `full` per type.
- Endringer som gjelder alle miljøer, må gjøres i hvert miljø. Det er bevisst, så de kan promoteres ett
  miljø om gangen.
- Endringer i Admin GUI blir overskrevet eller blir stående som avvik. De tas ikke inn i Git via ny eksport.
  Opprydding i miljøene gjøres som endringer i filene.

## Referanser

- `realm-config/README.md`, «Filer per miljø» og «Fra eksport til config»
- `realm-config/scripts/split.py`
