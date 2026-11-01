# Round 05: tags and search

**Brief:** [`../../plan/05-tags-search.md`](../../plan/05-tags-search.md).
**Result:** done.
- 72 tests pass (54 before this round, 18 new).
- Tag and text filters combine with both sorts, in the API and on the page.
- The browser check passes at 375 and 1440 px, light and dark.
- The owner gave their visual yes for round 04 before this round started.
- The 250k context alert came mid-round; the session compacted once (FB-007 seen again).

## 1. Plan
Slices:
1. The domain: `parseFilters(params)` for `tag` (the tag pattern) and `q` (trimmed, blank means no
   search, at most 50 characters).
2. The store: a tag index `["ideas_by_tag", tag, id] → id`, written in the same atomic commit as the
   idea; `list({limit, sort, tag, q})`.
3. The API: `tag` and `q` on `GET /api/ideas`, refused with 400 when invalid.
4. The page: a search form, tag links, an active-filters line with "Clear filters", sort links that
   keep the filters.

## 2. Spec first
- **API spec:** the tag index as a data source; `tag` and `q` inputs with their refusals (`bad_tag`,
  `too_long`); invariants 13–16 (filters combine with AND; `q` is a plain substring, never a regular
  expression; a filtered read looks at no more than 500 candidates; the tag keys land with the idea).
- **Page spec:** the `GET /?tag&q` row; invariants 9–11 (filters are kept by every link and the form;
  filter values are escaped; every link to `/` sets `aria-current` itself).

## 3. Tests first
- `domain/filters_test.ts` (5): a valid pair; a bad tag; a blank `q`; 50 and 51 characters; no
  parameters.
- `store/search_test.ts` (7): three tags across six ideas; `q` is case-insensitive over title and
  description; `.*` matches only those two characters; tag AND `q` with both sorts; the limit; the
  500 bound; the tag keys written at `create`.
- `api/search_test.ts` (3): filtered 200; `tag=Bad!` gives 400 `bad_tag`; a 51-character `q` gives
  400 `too_long`.
- `pages/home_test.tsx` +3: "filters combine and are kept" (sort links, tag links, the hidden form
  fields, "Clear filters", exactly one current link); "an invalid filter is dropped and no match says
  so"; "a script search is shown as text".

## 4. Build: where I got stuck
| Problem | Cause | Fix |
| --- | --- | --- |
| The 250k alert landed between the two edits of `store/ideas.ts` | at the alert only closure tools pass, so the second edit (`ListOptions`, `SEARCH_BOUND`) was refused | the handover carried the exact missing lines; applied first thing after compaction; **FB-007** |
| The checkpoint failed with only a command name | `doc-links` L3: `AGENTS.md` (the new `demo/` map line) changed without `factory-architecture.md`, which sources it | a "Demo playground" row in that spec's components table; the owner had to run `doc-links` to see the rule; **FB-007** |
| After compaction every tool call failed once | the hook said "native usage temporarily unavailable" while the transcript was being written | a retry passed, as in round 03 |
| `deno lint`: `require-await` on `list` | `list` now returns either branch's promise and awaits nothing | `async` removed |

The page's lenient rule needed one decision: `?tag=Bad!&q=dark` keeps the search and drops only the
tag. Each filter is parsed alone with the same `parseFilters` the API uses, so the page and the API
cannot disagree on what is valid; they differ only in what they do with a refusal.

## 5. Batteries
```text
deno fmt --check         Checked 39 files
deno lint                Checked 35 files, clean
deno check               0 errors
deno test -A             ok | 72 passed | 0 failed
deno task build          built in 1.14 s
```

Smoke test: port 8200, a fresh `KV_PATH` seeded with six tagged ideas over the API.
```text
6 × POST /api/ideas                              -> 201
GET /api/ideas?tag=ui                            -> 200
GET /api/ideas?tag=ui&q=dark&sort=votes          -> [("Dark mode", 2), ("Dark API docs", 0)]
GET /api/ideas?q=.*                              -> count 0
GET /api/ideas?tag=Bad!                          -> 400 {"error":"invalid","field":"tag","reason":"bad_tag"}
GET /api/ideas?q=<51 × x>                        -> 400 {"error":"invalid","field":"q","reason":"too_long"}
GET /?tag=Bad!                                   -> 200 (tag dropped)
GET /?sort=votes&tag=ui&q=dark                   -> exactly one aria-current="page" or "true"
```

Screenshots: `img/05-375-light.jpg` (tag + search + votes sort), `img/05-375-dark.jpg` (tag only),
`img/05-1440-light.jpg` (search only), `img/05-1440-dark-empty.jpg` (no match).

Tag links reuse the tag chip's colours (text on `--color-tag`), already checked for AA in round 03.

## 6. Known limits
- Ideas stored before this round have no tag keys, so `?tag=` skips them. As in round 04, only the
  local `data/` of earlier rounds is affected.
- A filtered list reads at most the 500 newest candidates, then sorts those. Past 500 ideas, a
  search misses the older ones; the spec says so and a test pins it.
- No full-text index, stemming or ranking: the brief keeps search to a substring.

## 7. Feedback from this round
- **FB-007** (medium), seen again: a half-applied edit cannot be completed at the alert, and the
  checkpoint refusal hides the failing rule.
- What worked: the "exactly one current link" assertion from round 04 caught nothing new, because the
  explicit `aria-current` rule was written into the spec before the code this time.
