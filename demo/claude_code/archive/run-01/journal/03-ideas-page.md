# Round 03: the ideas page (SSR list and an island form)

**Brief:** [`../../plan/03-ideas-page.md`](../../plan/03-ideas-page.md).
**Result:** done.
- 38 tests pass (26 before this round, 12 new).
- The HTTP smoke test passes.
- The browser check passes at 375 and 1440 px, light and dark.
- The copy's factory checks are green.
- The owner's visual yes is still pending (`ui.md`).

## 0. Before the round: the context alert and the repository layout
Round 03 started just before the 250k alert. Closure then deadlocked: see **FB-007**. The owner ran the
checkpoint in their own terminal (`Checkpoint: PASS`), then `/compact`.

The first two tool calls after compaction were refused with "native usage temporarily unavailable;
retry after transcript persistence". The retry went through. This was transient, so it gets no FB.

Then the owner's layout decision:
- the briefs moved to `demo/plan/`;
- `/demo/*/*` is ignored, except `demo/plan/*` and `demo/*/feedback_for_aeion/`;
- the parent `AGENTS.md` maps `demo/`.

Commit `dc7ed7e`: "Keep demo/ local except the harness feedback". The parent `AGENTS.md` was at 120
lines, so the `loops/` entry had to lose a line first. That is FB-004 again.

(Revised by the owner during round 05: only `demo/<harness>/workspace/` is ignored now; journals,
batteries and renders are tracked.) From now on, a round's app, journal and render stay local. The parent repository receives only
feedback files and base fixes.

## 1. Plan
Skill `plan`; the brief is approved by the owner's plan.

Slices:
1. `domain/form.ts`: parse the form, map reasons to messages.
2. `pages/home.tsx`: the handlers take the store as a parameter, plus the page.
3. The fields component, shared by the page and the island.
4. The island.
5. CSS tokens.
6. `csrf()` in `main.ts`.

The route file only wires things together. `store/shared.ts` gives the page and the API one lazily
opened KV.

## 2. Spec first
`knowledge/components/idea-board-page.md` was written before any code. It has eight sections, and
each invariant names its test. It is linked from `components/index.md`. The API spec gained the
`store/shared.ts` source.

## 3. Tests first
- `domain/form_test.ts` (4 tests): tags are split, trimmed and lowercased, empties dropped; missing
  fields read as `""`; a file reads as `""`; every reason code has a message.
- `pages/home_test.tsx` (8 tests): the empty state, newest first, a no-JS form, 303, 400 with the
  values kept, `aria-describedby`, escaping, no inline styles.

  The tests mount the route alone with `new App<State>().route("/", {handler, component}).handler()`
  on an in-memory KV: no build, no file-system router.

## 4. Build: where I got stuck
| Problem | Cause | Fix |
| --- | --- | --- |
| `deno check`: the handler is not assignable to `RouteHandler<unknown, unknown>` | the test used `new App()`, whose State is `unknown` | `new App<State>()` |
| `deno check`: `PageProps<HomeData>` is not assignable to `RouteComponent<unknown>` | `App.route` types every page component's data as `unknown` | the test casts the component, with a comment; `fsRoutes` needs no cast |
| `deno lint`: `jsx-key`, twice | `.map` without `key` | `key={tag}` and `key={idea.id}` |
| escaping test failed | Preact escapes `<` but not `>` in text: `&lt;script>alert(1)&lt;/script>` | the assertion now checks the real encoding; a tag cannot open without `<`, so the test still proves the invariant |
| `guard-bash` refused `kill $(cat $S/serve03.pid)` | bounded intake cannot size a variable path | `head -n 1`; see **FB-008** |

`deno fmt` rewrote two files after Write (`IdeaFormFields.tsx`, `home.tsx`). The edit hooks reported
it, and the files on disk are the formatted ones.

## 5. Batteries
```text
deno task check          fmt + lint + check: clean (29 files)
deno test -A             ok | 38 passed | 0 failed
deno task build          built in 1.07s (route chunk index 2.94 kB, API 64.34 kB)
```

Smoke test: `PORT=8200`, with a scratch `KV_PATH`, after `lsof` showed the port was free.
```text
h1: 1
POST / title=Offline mode&tags=UX, sync   -> 303 http://localhost:8200/
POST / title=x                            -> 400, "at least 3"
POST / with Origin: https://evil.example  -> 403 (csrf)
GET /api/ideas?limit=5                    -> the idea, tags ["ux","sync"]: one store for both
island markers present                    -> frsh:island:NewIdeaForm
```

Browser, with the built-in pane:
1. At 375 px, light: title "x", then Enter. The island fetched `/api/ideas`. The page did not
   navigate (1 navigation entry). The field showed "Use at least 3 characters." with
   `aria-invalid="true"`.
2. A valid title with tags `UI, a11y`: the page reloaded. The new idea was first, with `#ui#a11y`.
3. Screenshots: `img/03-375-light-error.jpg`, `img/03-375-dark.jpg`, `img/03-1440-light.jpg`,
   `img/03-1440-dark.jpg`.

Contrast, from the tokens and the WCAG formula: every text pair is at least 6.07:1, which passes AA.
The lowest pair is light `danger` on `bg`.

Factory checks in the copy, with `app/_fresh` removed first (FB-005):
```text
knowledge-check   PASS (0 problem(s))
doc-links         PASS (76 links checked)
repo-health       AGENTS.md 120 lines, work/current.md 13, 8 top-level folders
```

## 6. Sign-off, without git
- The copy's `CHANGELOG.md` has its line for the ideas page.
- The copy's `work/current.md` now covers rounds 04–07. It also had a wrong brief path, which I
  fixed: from `work/`, the briefs are `../../../plan/`.

## 7. Feedback from this round
- **FB-007** (medium): at the alert, the checkpoint command is admitted only in its exact relative
  form, so a different working directory deadlocks closure. The refusal also does not name the bad
  heading.
- **FB-008** (low): bounded intake calls a variable-path `cat` a dump.
- **FB-004**: seen again in the parent.
- What worked: writing the spec's invariants with their test names first made the test file almost
  dictate itself. The edit hooks caught the formatting drift on every write.
