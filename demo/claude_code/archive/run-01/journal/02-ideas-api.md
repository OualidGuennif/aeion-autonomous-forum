# Round 02: Ideas API on Deno KV

**Brief:** [`../../plan/02-ideas-api.md`](../../plan/02-ideas-api.md).
**Result:** done. 26 tests pass, the HTTP smoke test passes, and the factory checks are green.

## Plan (skill `plan`)
- **Files:**
  - `app/domain/idea.ts` (type and validation);
  - `app/store/ideas.ts` (KV store);
  - `app/store/kv.ts` (opens KV from `KV_PATH`);
  - routes in `app/api/app.ts`;
  - a lazy KV in the catch-all route;
  - `app/testing.ts` (in-memory KV helper, used by two test files).
- **Not done:** votes, tags filter, search.

## Steps
1. **Spec first.** I extended `knowledge/components/idea-board-api.md` before any code:
   - the field table, in check order, with every reason code;
   - every status, from 201 to 415;
   - five new invariants (5–9), each naming its test.

   The spec is more precise than the brief. It adds the `body` field (`malformed_json`, `not_object`) and `not_string` / `not_array`. Spec docs are the place for that precision.
2. **Tests first.** 21 new tests:
   - `domain/idea_test.ts`: one test per reason code, plus the "first failing field wins" order;
   - `store/ideas_test.ts`: create/get, newest first within one millisecond, limit;
   - `api/ideas_test.ts`: every status, plus "invalid writes nothing", which lists the whole KV and expects 0 keys.

   Before the code: `Found 19 errors. Type checking failed`.
3. **Build.** Three choices:
   - **Validation** is a pure function returning `{ ok, value } | { ok: false, field, reason }`.
   - **Ids:** `monotonicUlid()`, because plain ULIDs created in the same millisecond do not sort by creation.
   - **The KV store** is created with an atomic `check(versionstamp: null)`, so an id collision can never overwrite.
4. **Problems hit.**
   - **`Deno.Kv` types missing.**
     - `--unstable-kv` (`"unstable": ["kv"]`) enables the runtime API but not the types.
     - I added `"deno.unstable"` to `compilerOptions.lib`.
   - **`deno fmt` rewrote my files** to its default width of 80. I set `"fmt": {"lineWidth": 120}` in `deno.json`, in line with the factory's own code style.
   - **Opening KV at import time** would create `data/` during `vite build`, because the build imports the route module. The route now opens KV on the first request. I checked: `deno task build` leaves no `data/` folder (invariant 9).
5. **The framework at work.**
   - The copy's `after-edit.py` has a slot: "Add your project's per-file checks here (formatter, linter)". I filled it.
   - Each edited `app/**/*.ts(x)` file is now formatted and linted with the app's own `deno.json`.
   - A failure blocks the edit and shows Deno's diff. I probed it with a badly formatted file: blocked, exit 2, with the diff.
   - The copy's 522 hook tests still pass.
   - The doc hint fires as designed: `app/domain/idea.ts is covered by knowledge/components/idea-board-api.md`.

## Result
```text
deno task check → exit 0
deno task test  → ok | 26 passed | 0 failed
POST /api/ideas {"title":"Dark mode","tags":["ui"]}  → 201 {"idea":{"id":"01M61E6E…","votes":0,…}}
POST {"title":"x"}                                   → 400 {"error":"invalid","field":"title","reason":"too_short"}
POST text/plain                                      → 415 {"error":"unsupported_media_type"}
GET  /api/ideas?limit=5                              → [2, ["Offline mode","Dark mode"]]
GET  /api/ideas?limit=0                              → 400 field limit, out_of_range
build leaves no data/                                → data_after_build=none
knowledge-check PASS · doc-links PASS (66 links) · repo-health clean
```
