# 03 — The ideas page: SSR list and an island form

## Plan (and what was not done)
New: `app/routes/index.tsx` (handler + page), `app/islands/NewIdeaForm.tsx`, `app/domain/form.ts`,
`app/domain/messages.ts`, `app/store/database.ts` (one store for page and API), the state field in
`app/utils.ts` and the middleware in `app/main.ts`, plus `app/domain/form_test.ts` and
`app/routes/index_test.ts`. Styles for the form and the list. Not done: votes, filters, pagination.

## Order followed
1. Spec first: `knowledge/components/idea-board-page.md`, linked from the components index.
2. Tests first: `form_test.ts` and `index_test.ts`, run against nothing — type errors for the
   missing modules, as expected.
3. Implementation: form mapping → messages → shared store → state/middleware → island → route.

## What went in
- The page handler returns `{data}` (Fresh 2's page response); on a bad post it returns
  `{status: 400, data}` so the same page comes back with the typed values and the API's own
  `{field, reason}`. A good post stores the idea and answers `303 location: /`.
- `toIdeaInput` splits, trims and lowercases the comma-separated tags and drops blanks, but keeps
  duplicates so the shared `validateIdea` is the one that refuses them.
- `describeError` turns a reason code into one sentence, used by the page and by the island.
- The island is the only place the form markup lives; Fresh server-renders it, so the form works
  with JavaScript off, and the island only intercepts `submit`, posts JSON to `/api/ideas` and shows
  the refusal under the failing control (`aria-describedby="<field>-error"`).
- The store moved into `app/store/database.ts` as one lazy promise, placed in `ctx.state.ideas` by a
  middleware in `main.ts`, so the page and the API share one KV connection and tests inject a
  `:memory:` store instead of opening the file.

## Where I got stuck, and what fixed it
1. **`defaultValue` is not SSR-safe.** Preact's `preact-render-to-string` renders
   `defaultValue={x}` as the attribute `defaultvalue="x"`, which a browser ignores: an invalid post
   would have come back with an empty form. Switched the inputs to `value={...}` and the textarea to
   child content, which render as `value="x"` and `>x</textarea>`. The page test that asserts kept
   values caught it.
2. **Escaping expectation was too strict.** Preact escapes `<` but not `>`: a `<script>` title
   renders as `&lt;script>alert(1)&lt;/script>`, which is safe (no tag can start) but not the exact
   string I first asserted. The test now asserts the escaped `<` and the absence of the raw tag.
3. **A test looked for the error on the wrong field.** Both a too-short title and duplicate tags were
   in one request; the validator refused the title first, as the spec says, so the tags error never
   rendered. Fixed by testing one failure per request.

## Verification
```text
deno fmt --check   PASS (28 files)
deno lint          PASS (26 files)
deno check         PASS
deno test -A       50 passed, 0 failed
deno task build    PASS
PORT=8787 KV_PATH=/tmp/pi-ideas-03.sqlite3 deno task start
  GET  /                       200, one <h1>Idea Board</h1>, "No ideas yet." when empty
  POST / title=Offline mode    303, then the idea is on the page
  POST / title=x               400, the page contains "at least 3"
  the served page has name="title", name="description", name="tags" and a module script
```
Factory checks in the copy: `knowledge-check` PASS, `doc-links` PASS (95 links), `repo-health` PASS.

## Guard refusals
One: a `cd … && cat > probe` heredoc was refused by guard-bash (bounded intake: a dump after a
`cd`). Right call for a dump; I wrote the probe with the file-write tool instead, which is also what
the refusal's advice suggests in spirit.

## Feedback
None new.

## Result

```text
Result:          done
Tests:           50 passed, 0 failed (deno test -A); fmt, lint, check, build and the four page
                 smoke checks all pass
Factory checks:  knowledge-check PASS, doc-links PASS, repo-health PASS
Guard refusals:  1 (bounded-intake on a heredoc dump after a cd; right, avoided with the write tool)
Human help:      0
Feedback:        none
Elapsed:         ~50 minutes
```
