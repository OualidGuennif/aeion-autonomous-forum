# Round 07 — Architecture: Graphify use cases and an Archify gallery

Plan (skill `plan`, skill `graph`): Part A — answer five real structural questions with the parent's
`tools/architecture/ask --root opencode/workspace`, keep every command and its real output in
`render/graphify/use-cases.md`, and name where the graph was silent or wrong. Part B — 14 small
Archify diagrams (system, sequence, use case, lifecycle, data flow, timeline), each validated,
rendered and browser-checked, then a `render/index.html` gallery with a card and a live thumbnail
per diagram. Not done: Archify `sources` evidence links (need git), any Graphify command that calls
a model.

What happened:
- Graph of the workspace: 1415 nodes, 2470 edges, 207 files; the copy's own venv was never
  installed (the parent's graph covers `--root opencode/workspace`).
- The five answers (`render/graphify/use-cases.md`, raw output beside it): impact of the store
  (11 users at depth ≤ 2), the POST → KV path (four hops named from code, method calls have no
  graph edges), untested files (main.ts, vite.config.ts, the two route files are only exercised by
  the build/smokes), spec coverage (found a real gap: `app/domain/search.ts` had its `Spec:` line
  but no `sources` entry — fixed here), and hubs (`app/store/ideas.ts` first, 9 users).
- Feedback FB-001: after that frontmatter fix, `ask` kept returning the stale answer and the old
  edge counts (2470); deleting `workspace/.aeion/graph` and re-running gave 2473 edges and the
  correct `[both]`. The incremental freshness check missed a frontmatter-only edit.
- Part B: 14 diagrams, `validate --quality showcase` 0 errors/0 warnings each, rendered, then
  `browser-check --summary` → `status: pass` for all 14 (receipts `*.browser-check.json`).
  Layout judgment iterations were needed: label clearances (three diagrams), a sequence self-message
  (moved to a note), lifecycle `schema_version` 3, timeline `meta.evidence: observed`, dataflow
  label widths, and a `return` variant the dataflow schema does not allow (used `dashed`).
- Gallery: `render/index.html`, 14 cards with live iframes, opened in the in-app browser — 14 cards,
  14 iframes, no console errors.
- Factory checks: PASS (evidence/07-factory.txt).

Result:          done
Tests:           51 passed, 0 failed (deno test -A); 14 diagrams validate/render/browser-check PASS;
                 gallery loads with no console errors
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  1 (a `cat` dump after cd — bounded intake; read with head instead)
Human help:      0 after the pre-authorization
Feedback:        FB-001 new (ask incremental rebuild missed a frontmatter-only edit); FB-002 (the
                 `cat`-after-`cd` refusal shape, seen in rounds 01, 04, 07) and FB-003 (concurrent
                 writers on the shared working tree) filed when the run closed
Elapsed:         25 minutes
