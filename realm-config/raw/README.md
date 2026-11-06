# raw/ – råeksporter (committes ALDRI)

Alt i denne mappen unntatt denne filen er ignorert i `.gitignore`.

Legg partial export fra hvert miljø her:

| Fil             | Kilde                                                   |
|-----------------|---------------------------------------------------------|
| `dev.json`      | Admin GUI i dev → Realm settings → Action → Partial export |
| `betatest.json` | samme i betatest                                        |
| `prodtest.json` | samme i prodtest                                        |
| `prod.json`     | samme i prod                                            |

Huk av **Include groups and roles** og **Include clients**. Eksporten tar ikke med brukere,
og secrets er maskert (`**********`).

Deretter saner og deler du opp eksporten. Se [../README.md](../README.md#migrering):

```shell
python realm-config/scripts/kcconfig.py sanitize realm-config/raw/dev.json realm-config/raw/dev.sanitized.json \
  --client-secret-env schedule-job=SCHEDULE_JOB_CLIENT_SECRET
```

Slett filene når migreringen er ferdig. Eksportene inneholder konfigurasjon som er intern for miljøet.
