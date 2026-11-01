# 05 — Tags filter and text search

## Goal
A visitor narrows the list by tag and by text, from the API and from the page.

## User stories
- As a visitor, I click a tag and see only the ideas with that tag.
- As a visitor, I type "dark" and see the ideas whose title or description contains it.

## Spec
**Inputs.** `GET /api/ideas?tag=<tag>&q=<text>&sort=…&limit=…`; on the page `GET /?tag=…&q=…&sort=…`.

| Parameter | Rule | Reason code |
| --- | --- | --- |
| `tag` | optional, `^[a-z0-9-]{1,20}$` | `bad_tag` |
| `q` | optional, trimmed, 1–50 characters, case-insensitive substring of title or description | `too_long` |

**Outputs.** `200`, the same shape as the list, filtered; filters combine with AND and keep the requested sort.
The page shows the active filters and a "Clear filters" link; tags are links to `/?tag=<tag>`.

**Storage.** A tag index `["ideas_by_tag", tag, id] → id`, written atomically with the idea. A tag
filter reads the index, never every idea. Text search scans at most the 500 newest ideas (a stated bound, not a full-text engine).

**Invariants.**
1. Creating an idea writes its tag keys in the same atomic operation.
2. A tag filter returns exactly the ideas with that tag (test with 3 tags across 6 ideas).
3. `q` matching ignores case and does not interpret regular expression characters (`q=.*` finds only a literal `.*`).
4. Filter values shown on the page are escaped.

## Acceptance checks
```bash
cd app && deno test -A
curl -s 'localhost:8000/api/ideas?tag=ui' | jq '[.ideas[].tags | index("ui")] | all'   # true
curl -s 'localhost:8000/api/ideas?q=DARK' | jq '.count'
curl -s -o /dev/null -w '%{http_code}' 'localhost:8000/api/ideas?tag=Bad_Tag'        # 400
```

## Tests required
- `app/store/search_test.ts`: tag filter, text filter, both together with each sort, literal regex characters, the 500 bound.
- `app/api/search_test.ts`: the refusals.

## Out of scope
Full-text ranking, fuzzy matching, tag renaming, tag counts.

## Expected result
Filtering by tag and text works on the API and the page. Specs updated.
