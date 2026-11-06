# base/ – konfigurasjon som er felles for alle miljøer

Filene genereres første gang av `kcconfig.py split`. Rediger dem ikke for hånd før det.
Etterpå er dette filene du endrer i PR-er.

| Fil                       | Innhold                                                          |
|---------------------------|------------------------------------------------------------------|
| `realm.json`              | realm-innstillinger (tokens, sesjoner, login, flow-bindinger, SMTP) |
| `roles.json`              | realm- og klientroller                                           |
| `client-scopes.json`      | client scopes og standard-scopes                                 |
| `authentication.json`     | authentication flows, authenticator-config og required actions   |
| `clients.json`            | klienter (uten secrets)                                          |
| `identity-providers.json` | IdP-er og mappers (secrets som `${vault.*}`)                     |
| `groups.json`             | grupper                                                          |
| `components.json`         | komponenter, for eksempel user profile (aldri realm-nøkler)      |

Alle filene er gyldig realm-JSON. Lister flettes etter nøkkel (`clientId`, `alias`, `name` osv.)
med overlay fra `envs/<miljø>/`.
