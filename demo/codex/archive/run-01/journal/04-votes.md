# Round 04 — Votes and sorting

Date: 2026-10-28. All rounds authorized by the owner together.

## Plan and implementation

Plan: add optimistic votes, atomic vote-index maintenance, recent/vote sorting and native/island vote controls. Red output precedes the new methods. The 25-concurrent-vote test ends at exactly 25 with one index key; injected commit conflicts prove the ten-attempt cap and API 503 busy. Production smoke sends three concurrent HTTP votes and reads exactly three. A checked, idempotent startup backfill keeps earlier-round persisted ideas in the new index; it refuses a concurrent startup change instead of writing stale index data. Vote retries use short bounded jitter to avoid synchronized contention. Native vote handler tests verify 303 and retained sort. No accounts, vote deduplication, filters or rate limiting.

## Validation

Acceptance commands were run from workspace/app; factory checks from workspace.

- [fmt](evidence/04/fmt.log)
- [lint](evidence/04/lint.log)
- [check](evidence/04/check.log)
- [test](evidence/04/test.log)
- [build](evidence/04/build.log)
- [knowledge-check](evidence/04/knowledge-check.log)
- [doc-links](evidence/04/doc-links.log)
- [repo-health](evidence/04/repo-health.log)
- [smoke](evidence/04/smoke.log)

[Source checksums](evidence/04/source-sha256.txt) identify the local, git-ignored application.
No base-factory change, global tool install or push.

```text
Result:          done
Tests:           40 passed | 0 failed; production smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 during this round
Human help:      0 additional owner actions; rounds 01–07 already authorized
Feedback:        none new
Elapsed:         1 minutes (rounded, earliest retained round evidence to commit; excludes any earlier work)
```
