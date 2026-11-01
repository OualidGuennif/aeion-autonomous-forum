# Round 03 — The ideas page

Plan (skill `plan`): write a new component spec `knowledge/components/idea-board-page.md` and link
it from the index, then three thin pieces — `app/domain/form.ts` (comma-separated tag parsing and
form-to-domain validation), `app/islands/NewIdeaForm.tsx` (HTML form first, fetch enhancement when
JS runs), and `app/routes/index.tsx` (GET/POST handler + `HomeView`) — with `form_test.ts` and
`index_test.ts` written before the code and seen red. Not done: votes, filters, client routing.

What happened:
- Spec written first (eight sections) and linked from `knowledge/components/index.md`.
- Red first: `deno test -A domain/form_test.ts routes/index_test.ts` failed on the missing modules
  (evidence/03-red.txt).
- `app/routes/index.tsx` opens the process KV lazily (`defaultStore ??= ...`) so importing it from
  a test does not touch the disk, and exports `handleGet`, `handlePost` and `HomeView` so the page
  tests pass their own `:memory:` store and render with `preact-render-to-string`.
- One small catch in the escaping test: Preact's JSX precompile escapes `<` to `&lt;` and leaves
  `>` literal (`&lt;script>alert(1)&lt;/script>`), which opens no HTML tag; updated the test
  assertion to match what the renderer produces.
- Green: 31 tests (`deno test -A`), fmt/lint/check pass (evidence/03-quality.txt), build
  (evidence/03-build.txt). HTTP smoke from the brief: `<h1>Idea Board</h1>` count = 1, valid
  `POST /` → 303 and the idea appears on `GET /`, invalid `POST / -d 'title=x'` → 400 containing
  both `too_short` and `at least 3` (evidence/03-smoke.txt). Factory checks: PASS
  (evidence/03-factory.txt).

Result:          done
Tests:           31 passed, 0 failed (deno test -A); build PASS; HTTP smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 this round
Human help:      0 (all rounds pre-authorized)
Feedback:        none new
Elapsed:         15 minutes
