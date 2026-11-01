# 02 — Ideas API on Deno KV

## Goal
Create, list and read ideas through the API, stored in Deno KV, with strict validation.

## User stories
- As a contributor, I post an idea and get it back with its id.
- As a visitor, I list the newest ideas and open one by id.
- As a contributor who makes a mistake, I get a clear error naming the field.

## Spec
**Idea.** `{ id: string (ULID), title: string, description: string, tags: string[], votes: number, createdAt: string (ISO 8601) }`

**Inputs (`POST /api/ideas`, JSON body).**

| Field | Rule | Reason code on failure |
| --- | --- | --- |
| `title` | string, trimmed, 3–80 characters | `required`, `too_short`, `too_long` |
| `description` | optional string, trimmed, at most 500 characters, default `""` | `too_long` |
| `tags` | optional array, 0–5 unique items, each `^[a-z0-9-]{1,20}$` | `too_many`, `bad_tag`, `duplicate` |
| any other field | refused | `unknown_field` |

**Outputs.**

| Request | Response |
| --- | --- |
| `POST /api/ideas` valid | `201`, `{"idea": Idea}`, `votes` = 0 |
| `POST /api/ideas` invalid | `400`, `{"error":"invalid","field":"<name>","reason":"<code>"}` (first failing field, in the table order) |
| `POST /api/ideas` not JSON | `415`, `{"error":"unsupported_media_type"}` |
| `POST /api/ideas` body over 8 KiB | `413`, `{"error":"too_large"}` |
| `GET /api/ideas?limit=n` | `200`, `{"ideas": Idea[], "count": n}`, newest first, `limit` 1–50, default 20 |
| `GET /api/ideas?limit=0` (or 51, or not a number) | `400`, field `limit`, reason `out_of_range` |
| `GET /api/ideas/:id` | `200`, `{"idea": Idea}`; `404` `{"error":"not_found"}` |

**Storage (KV keys).** `["ideas", id] → Idea`. Ids are ULIDs, so key order is creation order.

**Invariants.**
1. Validation is a pure function (`app/domain/idea.ts`), tested without KV or HTTP.
2. The store takes its `Deno.Kv` as a parameter; tests use `Deno.openKv(":memory:")`.
3. An invalid request writes nothing.
4. The KV path comes from `KV_PATH` (default `./data/ideas.sqlite3`); the data folder is git-ignored.

## Acceptance checks
```bash
cd app && deno test -A
curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' \
  -d '{"title":"Dark mode","tags":["ui"]}'            # 201 with an id
curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' \
  -d '{"title":"x"}'                                  # 400 field title reason too_short
curl -s 'localhost:8000/api/ideas?limit=5'            # newest first
```

## Tests required
- `app/domain/idea_test.ts`: one test per reason code, plus trimming and defaults.
- `app/store/ideas_test.ts`: create then get; list order and limit; missing id.
- `app/api/ideas_test.ts`: every status in the Outputs table, and "invalid writes nothing".

## Out of scope
Votes, tags filtering, search, pagination cursors, editing or deleting ideas.

## Expected result
The API is complete for create/list/get. Spec `knowledge/components/idea-board-api.md` updated.
