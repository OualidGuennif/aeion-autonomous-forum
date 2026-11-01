# Round 02 — Ideas API on Deno KV

Plan (skill `plan`): extend the API spec first (idea shape, validation table, outputs, KV keys),
then three thin modules — pure validation `app/domain/idea.ts`, store over a `Deno.Kv` parameter
`app/store/ideas.ts`, routes `app/api/ideas.ts` — with one test file each, written before the code
and seen red. Not done: votes, tag filtering, search, pages with data, Docker.

What happened:
- Spec extended (all eight sections; sources now include domain, store and the ideas tests).
- Red first: `deno test -A` failed on the missing modules (evidence/02-red.txt).
- Two real stumbles, both caught by the tests:
  1. `Deno.openKv` is still an unstable API in Deno 2.9.8: `deno check` failed until
     `"unstable": ["kv"]` in `deno.json` plus `"deno.unstable"` in the compiler `lib` (the
     explicit `lib` list had excluded it).
  2. Same-millisecond ULIDs are not ordered: `@std/ulid`'s `ulid()` is random; the list test came
     back in creation order and the wrong one. `monotonicUlid()` keeps key order = creation order.
- `Deno.openKv` does not create the data folder — the first HTTP smoke returned "Internal server
  error" with `unable to open database file: ./data/ideas.sqlite3`. `app/store/kv.ts` now makes the
  parent directory first (one place opens the KV from `KV_PATH`).
- Choices within the brief's codes: a non-object body fails on the first field (`title`,
  `required`); a wrong-typed `title` is `required`, `description` `too_long`, `tags` `bad_tag` (the
  documented code of that field's rule set); unparseable JSON answers 415 like a wrong media type
  ("not JSON").
- Green: 24 tests (`deno test -A`), fmt/lint/check pass (evidence/02-quality.txt), build
  (evidence/02-build.txt). HTTP smoke from the brief: valid post 201 with id, `{"title":"x"}` →
  400 `title`/`too_short`, list newest first, `data/ideas.sqlite3` created
  (evidence/02-smoke.txt). Factory checks: PASS (evidence/02-factory.txt).

Result:          done
Tests:           24 passed, 0 failed (deno test -A); build PASS; HTTP smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 this round
Human help:      0 (all rounds pre-authorized)
Feedback:        none new
Elapsed:         20 minutes
