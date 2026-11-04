# Working in this repo

## Before adding a table or column
Two places holding the same fact drift apart and cause bugs that are very hard to trace. Before writing any Flyway migration that creates a table or adds a column:
1. **Look for what already records it.** Search migrations and entities for the concept and its synonyms (e.g. payout, disbursement, transfer, sweep; float, balance, snapshot; grant, product, client): `grep -rniE 'word1|word2' mpesa-service/src/main/resources/db/migrations` and `git grep -nE '@Table|@Column\(name' -- mpesa-service/src/main/java | grep -iE 'word1|word2'`. When a database is reachable, also list the live schema (`information_schema.columns`).
2. **Extend, don't duplicate.** If a table already records the concept, add the missing columns there or read it through the service that owns it. Never create a parallel table for the same fact.
3. **Don't copy derivable values.** Store a copy only when it must be frozen at a moment (e.g. a balance as Safaricom reported it), and name it as a snapshot.
4. **Say what you found.** In the task report or PR, state: "Schema check: found `<tables/columns>`; extending `<x>`" or "new table because `<reason>`".
