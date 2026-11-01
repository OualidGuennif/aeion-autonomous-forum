# Round 02 — Ideas API and KV persistence

Date: 2026-10-28. All rounds authorized by the owner together.

## Plan and implementation

Plan: implement pure validation, strict limit parsing, injected KV store and create/list/get routes. Tests were written first and failed on missing modules; see red.log. Monotonic standard-library ULIDs preserve order even for same-millisecond creation; createdAt derives from their timestamp. Content type, malformed JSON, 8192-byte limit, all validation reasons, missing IDs and invalid-writes-nothing are covered. Domain validation counts Unicode code points and refuses null tags. Factory graph rdeps/tests/spec output is retained. Live production smoke creates, lists and reads through the real Fresh catch-all. No votes or filters in this round.

## Validation

Acceptance commands were run from workspace/app; factory checks from workspace.

- [fmt](evidence/02/fmt.log)
- [lint](evidence/02/lint.log)
- [check](evidence/02/check.log)
- [test](evidence/02/test.log)
- [build](evidence/02/build.log)
- [knowledge-check](evidence/02/knowledge-check.log)
- [doc-links](evidence/02/doc-links.log)
- [repo-health](evidence/02/repo-health.log)
- [smoke](evidence/02/smoke.log)

[Source checksums](evidence/02/source-sha256.txt) identify the local, git-ignored application.
No base-factory change, global tool install or push.

```text
Result:          done
Tests:           28 passed | 0 failed; production smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 during this round
Human help:      0 additional owner actions; rounds 01–07 already authorized
Feedback:        none new
Elapsed:         2 minutes (rounded, earliest retained round evidence to commit; excludes any earlier work)
```
