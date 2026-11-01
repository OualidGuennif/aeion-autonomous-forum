# Round 05 — Tags filter and text search

Plan (skill `plan`): extend the specs first (tag index, `?tag=`/`?q=` rules and refusals, page
filters and escaping), then add `app/domain/search.ts` (pure filter validation), the tag index in
`app/store/ideas.ts` (`create` writes it in the same atomic), `search()` with the 500-idea bound,
and the API/page wiring. Tests first (`store/search_test.ts`, `api/search_test.ts`), seen red on
the missing `search`.

What happened:
- Red first: `deno test -A store/search_test.ts api/search_test.ts` failed on `store.search`
  (evidence/05-red.txt).
- Store: `create` now writes `["ideas_by_tag", tag, id]` keys in the same `atomic()`; `search`
  reads the tag index when a tag is given (never every idea), scans at most the 500 newest ideas for
  text, filters with literal `includes` (no regular expressions), keeps newest-first order and
  re-sorts by `(votes desc, id desc)` for `sort=votes`, then slices the limit.
- Domain: `validateFilters` (`bad_tag`, `too_long`, trimmed `q`); the page reuses it and drops
  invalid values (the page only generates valid links; the API is the strict one).
- Page: filter-aware sort links, tag links to `/?tag=…` keeping the sort, an active-filter line
  with `Clear filters`, and the escaping invariant test for the shown query.
- Green: 51 tests (`deno test -A`), fmt/lint/check pass (evidence/05-quality.txt), build
  (evidence/05-build.txt). HTTP smoke from the brief: `?tag=ui` all carry the tag, `?q=DARK`
  count 1, `?tag=Bad_Tag` → 400, the filtered page shows `Clear filters` and tag links
  (evidence/05-smoke.txt). Factory checks: PASS (evidence/05-factory.txt).

Result:          done
Tests:           51 passed, 0 failed (deno test -A); build PASS; HTTP smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 this round
Human help:      1 (owner asked to compact mid-round; checkpoint verified, run continued)
Feedback:        none new
Elapsed:         20 minutes
