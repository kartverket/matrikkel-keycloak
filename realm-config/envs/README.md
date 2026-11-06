# envs/<miljø>/ – miljøspesifikke avvik fra base

Én mappe per miljø: `dev`, `betatest`, `prodtest` og `prod`. Filene har samme navn og format som i
`base/`, men inneholder **bare det som avviker**, for eksempel redirect-URI-er, IdP-endepunkter,
`frontendUrl` og miljøspesifikke klienter.

Filene genereres av `kcconfig.py split`. Tomme overlays skrives ikke. En mappe som bare inneholder
`.gitkeep`, betyr at miljøet er helt likt base.

Endringer i base gjelder alle miljøer. Legg endringen i en overlay hvis den bare skal ut i ett
miljø først.
