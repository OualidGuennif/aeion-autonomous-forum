# Round 07 — Architecture: Graphify use cases and an Archify gallery

**Agent:** dsh (DeepSeek Harness) 0.2.1-rc.3, one session. **Date:** 2026-10-29.
**Brief:** [`plan/07-architecture.md`](../../../../plan/07-architecture.md).

## Plan

Part A: five real questions answered with `tools/architecture/ask --root dsh/workspace`, every command
and its real output kept in `render/graphify/use-cases.md`, and every answer checked against the code.
Part B: fourteen small Archify diagrams in `render/diagrams/`, each validated, rendered and
browser-checked, with `render/index.html` as the gallery. Out of scope: Archify `sources` links (they
need a git revision), LLM-inferred graph edges, `tools/architecture/refresh` (it refuses a plain
folder).

## Part A — Graphify

The graph on the final build: 1,520 nodes, 2,667 edges, 214 files, 0 unresolved `Spec:` lines and 0
unresolved frontmatter sources. The five answers are in `render/graphify/use-cases.md`:

1. **What breaks if I change the ideas store?** `rdeps app/store/ideas.ts --depth 2` → 13 depth-1
   importers (API, page, database, two route helpers, eight test files) and 4 depth-2.
2. **How does `POST /api/ideas` reach KV?** `path` gives two file hops; `callers
   app/store/ideas.ts:20` (language server) adds `app/api/app.ts:69` → `IdeaStore.create`, and the
   last hop is `kv.atomic()` in `createIdeaStore`. Both silent hops are named in the doc.
3. **Which source files have no test?** `untested` separates imported by a test (11), named by a test
   (19), reached through others (6) and reached by none (11). The app's real gap is
   `app/routes/ideas/[id]/vote.ts`, covered only by a curl.
4. **Which spec covers this file?** `spec`/`files` found six app-shell files whose `Spec:` line named
   the API spec while the API spec never listed them. Fixed by pointing them at
   `knowledge/components/idea-board-runtime.md` and adding them to its `sources`; `filters.ts` now
   names the API spec (it owns the `tag`/`q` reasons) and stays a source of the page spec.
5. **Where should a newcomer start?** `hubs` → `app/store/ideas.ts` (13), `app/domain/idea.ts` (8),
   `app/api/app.ts` (5).

### What the graph got wrong or was silent about

- The brief names `app/store/kv.ts`; the store is `app/store/ideas.ts`.
- No edge for `deps.ideas.create(...)` (an interface method) — only the language server sees it.
- No edge for `kv.atomic()`, `Deno.serve`, `fetch` — runtime APIs, not project symbols.
- **Stale frontmatter after a rebuild:** `ask` rewrote the graph but kept the old `sources`, so the
  six gaps persisted and looked authoritative. Only `rm -rf .aeion/graph` + `ask status` produced the
  new edges (2,662 → 2,667). Filed as FB-009.
- "No symbol" is not "no file": eight files, including `client.ts`, `_app.tsx` and `vite.config.ts`,
  have no extractable symbol; `spec` answers for them, `find` does not.
- `untested` means "no test file reaches it", not "untested in practice" (the curls).

## Part B — Archify gallery

Fourteen diagrams, each at most ten boxes:

| Family | Diagrams |
| --- | --- |
| System | `system-overview`, `deployment` (architecture) |
| Sequence | `page-load`, `create-idea`, `vote`, `filter-search`, `health-check` |
| Use case | `use-case-visitor`, `use-case-contributor`, `use-case-operator` (architecture; Archify has no use-case type, so actors are external components and use cases sit in a `region` boundary — said on the page) |
| Other | `idea-lifecycle` (lifecycle), `kv-keys` (erd), `request-dataflow` (dataflow), `build-rounds` (timeline) |

`render/index.html` is a static page with one card per diagram: title, the question, a live iframe
thumbnail scaled to a 400×300 box, and a link to the full artifact. It opens offline from `file://`;
a Chrome screenshot confirmed all fourteen thumbnails load.

## Checks

```text
$ tools/architecture/ask --root demo/dsh/workspace status
nodes: 1520  edges: 2667  files: 214  unresolved Spec: lines: 0  unresolved frontmatter sources: 0

$ node archify.mjs validate <type> diagrams/<name>.json --quality showcase
ok architecture|sequence|lifecycle|erd|dataflow|timeline … (9 artifact checks; composition showcase: 0 errors, 0 warnings)   x14

$ node archify.mjs render <type> diagrams/<name>.json diagrams/<name>.html --quality showcase   x14 -> 14 html
$ node archify.mjs browser-check diagrams/<name>.html --summary                                 x14 -> pass
   containment pass, readability pass, viewerChrome pass, themeStates pass
```

Chrome needed `HOME=/tmp/dsh-home ARCHIFY_CHROME_NO_SANDBOX=1` in this sandbox: the dsh seatbelt
blocks Chrome's own sandbox and its crashpad writes under `~/Library`, so the first run failed with
`sandbox initialization failed: Operation not permitted` and `Chrome DevTools read pipe failed`.
With a writable `HOME` and Archify's documented no-sandbox switch, all fourteen passed.

The app is unchanged in behaviour; its checks still pass:

```text
$ deno fmt --check && deno lint && deno check && deno test -A
ok | 79 passed | 0 failed
```

Factory checks on the copy:

```text
$ tools/knowledge/knowledge-check  -> RESULT: PASS (0 problems)
$ tools/knowledge/doc-links        -> RESULT: PASS (98 links checked)
$ tools/repo-health/repo-health    -> AGENTS.md 120 lines, work/current.md 12, 0 big files, 0 long comments
```

## Where I got stuck

- Archify's layout validator is strict: a boundary may not enclose a non-member (fixed by wrapping the
  whole Docker host), sequence messages must start at y ≥ 160 and may not run along a segment border
  (fixed by dropping optional segments and a self-message), and a canvas over 1085 px fails
  `desktop-readability` (fixed by narrowing two viewBoxes). Dataflow allowed at most five stages, so
  store and KV share the last one.
- `ask` kept stale spec frontmatter after a rebuild (FB-009); deleting `.aeion/graph` fixed it.
- Chrome could not start under the sandbox until `HOME` and `ARCHIFY_CHROME_NO_SANDBOX` were set.

## Result

```text
Result:          done
Tests:           14/14 diagrams validate, render and browser-check; 79 app tests still pass
Factory checks:  knowledge-check PASS, doc-links PASS (98 links), repo-health PASS
Guard refusals:  1 (Chrome sandbox / crashpad outside the workspace)
Human help:      0
Feedback:        FB-009 new
Elapsed:         ~70 minutes
```

## Commit

The sandbox cannot write `.git`; the owner runs:

```bash
cd /Users/wallseven/Desktop/aeion-factory
git add demo/dsh/README.md demo/dsh/journal/*.md demo/dsh/feedback_for_aeion/*.md demo/dsh/render
git commit -m "demo(dsh): round 07 architecture"
```

This is the only round whose commit stages `render/`: the gallery and its diagrams are the
deliverable, and `index.html` must open offline with every card loading. The fourteen
`*.browser-check.json` receipts sit beside the HTML as evidence.
