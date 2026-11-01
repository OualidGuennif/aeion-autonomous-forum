# 05 — Tags filter and text search

## Plan (and what was not done)
New: `app/domain/filters.ts` (one reading of `tag`/`q`), the tag index in the store, filtering and
the 500-idea scan window in `list`, the API refusals, and the page's search box, sort links kept,
active filters, "Clear filters" and tag-chip links. Tests: `store/search_test.ts`,
`api/search_test.ts`, three new page tests. Not done: full-text ranking, fuzzy matching, tag
renaming, tag counts, pagination.

## Order followed
1. Specs first: the `tag`/`q` rules, the tag index and the 500 bound in the API spec; the filter bar
   and escaped values in the page spec.
2. Tests first: the two required files plus page cases, failing on the missing `parseFilters`.
3. Implementation: filters module → store index and filtering → API → page.

## What went in
- A create writes `["ideas_by_tag", tag, id] → id` for each tag in the same atomic operation as the
  idea and its vote index entry.
- `list(limit, sort, filter)`: a tag filter reads the tag index (never every idea); `q` scans at most
  the newest 500 ideas and matches title or description literally, case-insensitively; the filters
  combine with AND; `votes` sorts by votes with ties newest first.
- `parseFilters` is shared: the API returns `400` for `tag=Bad_Tag` (`bad_tag`) and for `q` over 50
  (`too_long`); an empty `q` is no filter. The page shows the refusal in the page's own words and
  ignores the filter instead of failing.
- The page has a search form, sort links that survive filtering, an "active filters" line, "Clear
  filters", and tag chips that link to `/?tag=<tag>&sort=<sort>` (keeping `q`).

## Where I got stuck, and what fixed it
1. **A `python3` string edit quietly missed an import.** Adding `parseFilters` to `api/app.ts` by
   matching the formatted import block did not match (deno fmt had reflowed it), so the test run
   failed with `Cannot find name 'parseFilters'`. The type checker caught it in one run; fixed with a
   small targeted edit. Lesson kept: after a scripted edit, let `deno check` speak before believing it.
2. **A page test asked for tag/filter markup before there were filters.** The earlier sort test
   asserted `name="sort"` with no ideas; that is now also in the search form, so no change was
   needed there, but the new filter test needed an idea for the chip link to exist.

## Verification
```text
deno fmt --check   PASS (36 files)
deno lint          PASS (34 files)
deno check         PASS
deno test -A       79 passed, 0 failed
deno task build    PASS
PORT=8787 KV_PATH=/tmp/pi-ideas-05.sqlite3 deno task start
  GET /api/ideas?tag=ui     -> count 1
  GET /api/ideas?q=DARK     -> count 1 (case-insensitive, description text)
  GET /api/ideas?tag=Bad_Tag-> 400
  GET /?tag=ui              -> contains "Clear filters"
  GET /?q=DARK              -> contains name="q"
```
Factory checks in the copy: `knowledge-check` PASS, `doc-links` PASS (104 links), `repo-health` PASS.

## Guard refusals
None this round.

## Feedback
None new.

## Result

```text
Result:          done
Tests:           79 passed, 0 failed (deno test -A); fmt, lint, check, build and the five filter
                 smoke checks all pass
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        none
Elapsed:         ~50 minutes
```
