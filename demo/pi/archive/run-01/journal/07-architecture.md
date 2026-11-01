# 07 — Architecture: Graphify use cases and an Archify gallery

## Plan (and what was not done)
New: `render/diagrams/*.json` and `*.html` (fourteen), `render/index.html`, `render/graphify/` with
`use-cases.md` and its `raw/` outputs. One app doc changed: `knowledge/components/idea-board-api.md`
gained `app/vite.config.ts` as a source and a current home-page title. No app code changed. Not done:
`tools/architecture/refresh` (needs git; the copy is a plain folder), Archify `sources` evidence
links (need a revision and a repository URL), any Graphify command that calls a model (the Bash
guard refuses them, and the graph was already current).

## Order followed
1. The five `ask` questions first, with output kept in `render/graphify/raw/`.
2. The diagrams: JSON, then `finalize` (validate, render, browser-check) family by family.
3. The gallery, then the doc fixes the graph found, then the checks and the journal.

## What went in
| Family | Diagrams |
| --- | --- |
| System | `system-overview` (one Deno process: Fresh page, islands, Hono `/api`, domain, store, KV); `deployment` (Compose, container, health check, named volume) |
| Sequence | `seq-page-load` (SSR then hydration); `seq-create` (island → API → validation → one atomic write); `seq-vote` (check, move the vote key, retry, 503 busy); `seq-filter` (tag index, then 500-newest scan); `seq-health` (Compose → `deno eval` → `/api/health`) |
| Use cases | `use-case-visitor`, `use-case-contributor`, `use-case-operator` — architecture diagrams: actors outside, a system boundary inside. Archify has no use-case type, and the gallery says so. |
| Other | `idea-lifecycle` (stored, voted, never edited or deleted); `kv-model` (three key families); `request-dataflow` (untrusted fields → validator → store → KV); `aeion-round` (brief, spec, tests, build, checks, record, commit) |

The gallery `render/index.html` is one card per diagram with the question it answers, a live
`iframe` preview and links to the diagram and its JSON; it opens offline.

## What the graph said
- `ask rdeps app/store/ideas.ts --depth 2` → 13 direct dependents (nine are tests) + 4 at depth 2;
  the runtime consumers are `api/app.ts`, `routes/index.tsx`, `store/database.ts`, `utils.ts`.
- `ask path` → two file hops, `route → store/database.ts → store/kv.ts`. The real chain has three
  call hops; the graph cannot see the framework dispatch, the store injected through `ctx.state`,
  or `createIdeaStore` passed to `.then()`.
- `ask untested` → 12 files reached by none, only four of them app files (`main.ts`, the catch-all,
  `server.ts`, `store/database.ts`), all wiring proved by the live smoke and the container check.
- `ask spec` / `ask files` → `app/routes/index.tsx` is claimed by two specs on purpose; and
  `app/vite.config.ts` had a `Spec:` line but was missing from the spec's sources. That gap is
  fixed in this round: the file is now a source of `knowledge/components/idea-board-api.md`.
- `ask hubs` → the app's hubs are the store (13), the validator module (8), the Hono app (5).

Everything is written up with its raw output in `render/graphify/use-cases.md`, including a table of
where the graph is silent or wrong.

## Where I got stuck, and what fixed it
1. **Sequence geometry (4 diagnostics).** A segment edge ran along a message (`330` vs `332`), a
   two-line note reached the next message (`395` vs `416`), and a segment edge ran along a return
   message (`280` vs `278`). Archify names the exact `y` to use: moved the segment edges to 336 and
   282, and the message to 417. Both diagrams then passed.
2. **Workflow columns (2 diagnostics).** `toCol`/`col` had to be ≤ 5; then phases that share a
   boundary column are refused (`1..3` overlaps `0..1`), then two nodes in one lane at one column
   were 8 px apart. Fixed by ending phases at distinct columns (`0..0`, `1..2`, `3..4`, `5..5`),
   giving `Commit` its own `Ship` lane at column 5, and routing `read → plan` as `straight` (a
   `drop` between two nodes in one lane cannot satisfy its endpoint sides).
3. **Deployment ownership gates (6 diagnostics).** The `deployment-ownership` profile wants a
   security-group boundary, every component in a region, and every stateful component owned and
   private. Added a host region, the container region, the volume region and two security groups,
   tagged the KV file `owner: docker volume`, and moved `Health check` down: a boundary label above
   the container went off the canvas, then the label collided with the health arrow, then with the
   boundary title. Final layout puts the health check at `y 82`, the server at `170`, the label
   between them at `y 155`.
4. **Data flow collisions (3 diagnostics).** A return flow crossed the `parseFilters` node and a
   vertical channel merged with another flow. Removed the two redundant return flows (the graph
   still reads left to right; the cards say where data comes back) and moved the `400` label; the
   remaining flows plan themselves.
5. **The 250k alert fired mid-round**, between two diagram writes. The tool calls were refused with
   the closure message; the handover was written, `work/current.md` updated, and the checkpoint
   refused once because the handover's headings must be exactly `## Scope`, `## Completed`,
   `## Remaining`, `## Constraints`, `## Evidence`. After renaming five headings the checkpoint
   printed `Checkpoint: PASS`, the session compacted and resumed, and round 07 continued in place.
   That friction is filed as FB-002.

## Verification
```text
ask status                        1516 nodes, 2679 edges, 216 files; 0 unresolved Spec:, 0 unresolved sources
ask rdeps/path/untested/spec/files/hubs/tests/callers   exit 0 each, raw output in render/graphify/raw/
archify validate <type> <json> --quality showcase       14/14 exit 0
archify finalize ... --json                             14/14 exit 0, browser-check ran for each
render/index.html                       14 iframes, each src present beside it
deno fmt --check / lint / check / test -A   PASS / PASS / PASS / 79 passed
tools/knowledge/knowledge-check          PASS (0 problems)
tools/knowledge/doc-links                PASS (108 links)
tools/repo-health/repo-health            PASS (work/current.md 19 lines, 0 big files)
```
Factory checks in the copy: all three PASS.

## Guard refusals
1 — bounded intake on `cd … && … cat <raw output>`; right, fixed by `head -n 40` on literal paths
in its own command (knowledge/policies/bounded-intake.md).

## Feedback
FB-002 new: a verified checkpoint requires handover headings that nothing states.

## Follow-up after the owner opened the gallery
- Opening `render/diagrams/system-overview.html` from the Desktop showed "Access to the file was
denied": macOS withholds the Desktop folder from the browser, so any `file://` read there fails.
The diagram itself needs nothing — no `fetch`, no external script, only inline SVG and scripts — so
a server was never required either; it was a workaround for that permission.
- The gallery is now one self-contained file. `render/build_gallery.py` inlines the fourteen HTML
documents as JSON and draws each one from an iframe `srcdoc`, with an "Open full size" viewer that
reuses the same markup; nothing is read from disk but the page itself.
- The owner's "Loading preview…" stuck came from hiding the placeholder when `srcdoc` was assigned
rather than when the frame fired `load`. Fixed with a per-card `data-ready` attribute and one rule
(`.preview[data-ready="true"] .empty{display:none}`); the viewer also gained `‹ Prev`/`Next ›`,
arrow keys, Escape and focus return, so the fourteen diagrams can be browsed without leaving the
page.
- HTML is generated, not tracked: `render/.gitignore` covers `*.html` and `diagrams/*.html`, and
`git rm --cached` dropped the files committed earlier in this round. Git keeps the JSON diagrams,
the builder, `use-cases.md` and its `raw/` outputs; the earlier commit keeps the HTML in history.
- Verified on `file://` with headless Chrome at a 1500×26000 window: 14/14 previews rendered their
`<svg>`, 14/14 placeholders cleared, the viewer opened on the first diagram, `Next` moved to the
second with its own SVG, and Escape closed it. PASS.
- Feedback: FB-003 (the owner asked for this serverless gallery to become the factory's pattern,
with the page and CSS details) and FB-004 (round 07 observations, including Pi's missing `Stop`
hook found in round 00).
- Serving the folder stays possible for anyone who wants it:
`python3 -m http.server 8137 --bind ::` (IPv6 and IPv4, so `localhost` and `127.0.0.1` both work).

## Result

```text
Result:          done
Tests:           79 passed, 0 failed (deno test -A); 14/14 Archify validate and finalize gates, 0 failures
Factory checks:  knowledge-check PASS, doc-links PASS (108 links), repo-health PASS
Guard refusals:  1 (bounded intake on a chained cat; right, fixed by a separate head -n)
Human help:      0
Feedback:        FB-002, FB-003, FB-004 new
```
