# Round 03 — The ideas page: SSR list and an island form

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/03-ideas-page.md`](../../../../plan/03-ideas-page.md).

## Plan

Files: `app/routes/index.tsx` (+ `index_test.ts`), `app/islands/NewIdeaForm.tsx`,
`app/domain/form.ts` (+ test), `app/domain/messages.ts`, `app/deno.json`; the spec
`knowledge/components/idea-board-page.md` and its index row; the copy's `CHANGELOG.md` and
`work/current.md`. Out of scope: votes, sorting, filters, search, client-side routing.

## Spec and tests before the code

The page spec has the eight sections, the three outputs, five invariants each naming its test, and
the acceptance commands. I wrote `app/domain/form_test.ts` and `app/routes/index_test.ts` first and
ran them against the placeholder page: they failed on the missing module and exports, as intended:

```text
TS2307 [ERROR]: Cannot find module '.../app/domain/form.ts'.
TS2614 [ERROR]: Module '.../app/routes/index.tsx' has no exported member 'handler'.
TS2614 [ERROR]: Module '.../app/routes/index.tsx' has no exported member 'IdeaBoard'.
```

Then the page, the island, `form.ts` and `messages.ts` were written, and all 39 tests passed.

## What was built

- `GET /` lists the newest 50 ideas with title, description, tags, votes and date, or "No ideas yet.";
  a script title renders as text (`&lt;script>`, no raw tag).
- `POST /` reads the form, runs `toIdeaInput` (split on comma, trim, lowercase, drop empties) and the
  same `validateIdea` the API runs; valid is a 303 to `/`, invalid is a 400 page with the values kept
  and the message next to the field via `aria-describedby`.
- `NewIdeaForm` is a plain `method="post" action="/"` form first; the island intercepts submit, posts
  JSON to `/api/ideas`, reloads on 201 and shows the API's reason on 400.
- Every control has a label; the island is in the built page
  (`fresh-island__NewIdeaForm-….js`).

## Checks

```text
$ deno fmt --check   -> Checked 22 files, exit 0
$ deno lint          -> Checked 22 files, exit 0
$ deno check         -> exit 0
$ deno test -A       -> ok | 39 passed | 0 failed
   api/app_test 6, api/ideas_test 9, domain/form_test 3, domain/idea_test 11, routes/index_test 7, store/ideas_test 3
$ deno task build    -> exit 0
$ PORT=8000 deno task start
$ curl -s localhost:8000/ | grep -c '<h1>Idea Board</h1>'                  -> 1
$ curl -s localhost:8000/ | grep -c 'No ideas yet.'                         -> 1
$ curl -s -o /dev/null -w '%{http_code} %{redirect_url}' -X POST localhost:8000/ \
    -d 'title=Offline%20mode&tags=ux'                                       -> 303 http://localhost:8000/
$ curl -s -X POST localhost:8000/ -d 'title=x' | grep -c 'too_short\|at least 3' -> 1
$ # after the post: <h3>Offline mode</h3>, <span class="tag">ux</span>, 0 votes
$ curl -s -X POST localhost:8000/api/ideas -H 'content-type: application/json' \
    -d '{"title":"<script>alert(1)</script>"}'
$ curl -s localhost:8000/ | grep -c '<script>alert(1)</script>'             -> 0
$ curl -s localhost:8000/ | grep -c '&lt;script>alert(1)&lt;/script>'       -> 1
```

## Where I got stuck

- The first test run was a type-check failure, not a red assertion; that is the failing-first
  evidence for a module that does not exist yet.
- Preact's `textarea` keeps its value as children, not a `value` prop; the kept-values test checks the
  rendered `>later<` and passes.

## Result

```text
Result:          done
Tests:           39 passed, 0 failed (deno test -A); fmt, lint, check, build PASS; page and form curls PASS
Factory checks:  knowledge-check PASS, doc-links PASS (86 links), repo-health PASS
Guard refusals:  0
Human help:      0
Feedback:        none new
Elapsed:         ~35 minutes
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md
git commit -m "demo(dsh): round 03 ideas page"
```
