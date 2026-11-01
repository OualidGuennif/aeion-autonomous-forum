# Five questions answered against the implemented board

Date: 2026-10-28. Commands run from `demo/` with the parent's existing installation.
No LLM extraction or setup command ran. The graph includes the factory copy as well as the app.
The raw outputs below are retained separately beside this document. Each command exited 0.

## 1. What can a store change affect?

`rdeps` finds 12 direct importing files and two more at depth 2. The runtime consumers are
Hono, the home handler, the native vote handler and KV initialization. The other direct consumers
are tests. Inspect these interfaces before changing `create`, `get`, `list` or `vote`; imports
identify possible impact, not proof that every change breaks each consumer. Type-only imports
also count. Deeper consumers exist beyond the requested two-hop bound.

```text
$ ../tools/architecture/ask --root codex/workspace rdeps app/store/ideas.ts --depth 2
app/api/app.ts  [imports at app/api/app.ts:7]  depth 1
app/api/ideas_test.ts  [imports at app/api/ideas_test.ts:4]  depth 1
app/api/search_test.ts  [imports at app/api/search_test.ts:4]  depth 1
app/api/votes_test.ts  [imports at app/api/votes_test.ts:4]  depth 1
app/routes/ideas/[id]/vote.ts  [imports at app/routes/ideas/[id]/vote.ts:4]  depth 1
app/routes/index.tsx  [imports at app/routes/index.tsx:8]  depth 1
app/routes/index_test.tsx  [imports at app/routes/index_test.tsx:5]  depth 1
app/routes/vote_test.ts  [imports at app/routes/vote_test.ts:5]  depth 1
app/store/ideas_test.ts  [imports at app/store/ideas_test.ts:3]  depth 1
app/store/kv.ts  [imports at app/store/kv.ts:4]  depth 1
app/store/search_test.ts  [imports at app/store/search_test.ts:3]  depth 1
app/store/votes_test.ts  [imports at app/store/votes_test.ts:3]  depth 1
app/api/app_test.ts  [imports at app/api/app_test.ts:3] via app/api/app.ts  depth 2
app/routes/api/[...path].ts  [imports at app/routes/api/[...path].ts:3] via app/api/app.ts  depth 2

exit=0
```

## 2. How does POST /api/ideas reach KV?

| Hop checked in source | Evidence | What the graph proves |
| --- | --- | --- |
| Fresh filesystem routing → catch-all | `app/main.ts:4` calls `fsRoutes()`; `app/routes/api/[...path].ts:6` exports handler | Framework dispatch is not represented by an import edge; the real HTTP smoke proves it |
| Catch-all → `getIdeasStore()` | catch-all line 8 | Direct file import into `app/store/kv.ts` |
| `getIdeasStore` → open and initialize KV → `createIdeasStore(kv)` | `app/store/kv.ts:15–19`, with `openKv` at 6–11 | File imports, confirmed by reading the factory method; no claim that the shortest path expresses every call |
| Catch-all → `createApi({ideas}).fetch(ctx.req)` | catch-all line 8 | Direct import of the API factory; source proves the original request is forwarded |
| Hono POST route → parse/type/size checks → `validateIdea(body)` | `app/api/app.ts:16–35` | Import to domain and source-checked route registration; graph alone does not identify HTTP dispatch |
| API → `ideas.create(input)` | `app/api/app.ts:36` | Deno LSP `callers ...:create` resolves this exact method call |
| `create` → ID/time → checked atomic primary and index writes → `commit()` | `app/store/ideas.ts:8–25` | Source inspection fills the injected KV method hop; no local file node for Deno's native transaction implementation |
| Result → `{idea}` JSON 201 | `app/api/app.ts:36` | Source plus real HTTP acceptance |

The shortest catch-all → KV path is one import. It omits Hono, validation and `create`, so treating
that path as a complete request trace would be wrong. Separate API and store paths, LSP and reads
supply the missing hops. The architecture review also reproduced a real boundary gap: KV bootstrap
failed before Hono's error handler and returned plain text. The catch-all now returns JSON 500;
`api-startup-red.log` and `api-startup-green.log` in the round 07 evidence preserve both outcomes.

```text
$ ../tools/architecture/ask --root codex/workspace path app/routes/api/[...path].ts app/store/kv.ts
app/routes/api/[...path].ts -> app/store/kv.ts  [imports at app/routes/api/[...path].ts:4]

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace path app/routes/api/[...path].ts app/api/app.ts
app/routes/api/[...path].ts -> app/api/app.ts  [imports at app/routes/api/[...path].ts:3]

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace path app/api/app.ts app/store/ideas.ts
app/api/app.ts -> app/store/ideas.ts  [imports at app/api/app.ts:7]

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace callers app/store/ideas.ts:create
app/api/app.ts:36  createApi  [lsp call]
app/api/search_test.ts:24  (module)  [lsp call]
app/api/votes_test.ts:9  (module)  [lsp call]
app/routes/index.tsx:27  createHomeHandler  [lsp call]
app/routes/index_test.tsx:27  (module)  [lsp call]
app/routes/index_test.tsx:77  (module)  [lsp call]
app/routes/vote_test.ts:9  (module)  [lsp call]
app/routes/vote_test.ts:33  (module)  [lsp call]
app/store/ideas_test.ts:8  (module)  [lsp call]
app/store/ideas_test.ts:21  (module)  [lsp call]
app/store/ideas_test.ts:26  (module)  [lsp call]
app/store/search_test.ts:11  (module)  [lsp call]
app/store/search_test.ts:47  (module)  [lsp call]
app/store/search_test.ts:77  (module)  [lsp call]
app/store/search_test.ts:79  (module)  [lsp call]
app/store/votes_test.ts:9  (module)  [lsp call]
app/store/votes_test.ts:22  (module)  [lsp call]
app/store/votes_test.ts:23  (module)  [lsp call]
app/store/votes_test.ts:40  (module)  [lsp call]
app/store/votes_test.ts:68  (module)  [lsp call]

exit=0
```

## 3. Which files have no test pointing at them?

`untested` is structural reachability, not executed statement coverage. Among app code, only
`main.ts`, `routes/_app.tsx` and `start.ts` have no test import path. The production HTTP and
Chrome checks run these entry points, but those external checks do not create static test edges.

The store has eight direct importing test files; the startup regression reaches it through the
catch-all and API factory (without successfully opening KV). `api/app_test.ts` reaches it transitively through
Hono (and imports `BusyError`, even when the test only calls health). `NewIdeaForm.tsx` is reached
through the home route test; that renders HTML but does not run its browser event handlers. The
Chrome acceptance separately creates a uniquely titled idea and votes with the enhanced islands.
`query.ts` is transitively reachable through API, routes and the store. Some paths are type-only
imports. None of these edges proves a function executed or a behavior assertion exists: the
actual tests and browser readbacks are the evidence for that.

```text
$ ../tools/architecture/ask --root codex/workspace untested
47 source files: 12 imported by a test, 18 named by a test, 6 reached through others, 11 reached by none
untested: app/main.ts
untested: app/routes/_app.tsx
untested: app/start.ts
untested: loops/decisions-api/client.sh
untested: tools/claude-plugins/deno-lsp/scripts/deno-lsp
untested: tools/claude-plugins/native-lsp/scripts/jdtls-launch
untested: tools/claude-plugins/native-lsp/scripts/tsc-lsp
untested: tools/factory-tests
untested: tools/git-hooks/pre-commit
untested: tools/git-hooks/pre-push
untested: tools/release/release

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace tests app/store/ideas.ts
app/api/ideas_test.ts  direct
app/api/search_test.ts  direct
app/api/votes_test.ts  direct
app/routes/index_test.tsx  direct
app/routes/vote_test.ts  direct
app/store/ideas_test.ts  direct
app/store/search_test.ts  direct
app/store/votes_test.ts  direct
app/api/app_test.ts  transitive via app/api/app.ts
app/routes/api/handler_test.ts  transitive via app/routes/api/[...path].ts -> app/api/app.ts

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace tests app/islands/NewIdeaForm.tsx
app/routes/index_test.tsx  transitive via app/routes/index.tsx

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace tests app/domain/query.ts
app/api/app_test.ts  transitive via app/api/app.ts
app/api/ideas_test.ts  transitive via app/api/app.ts
app/api/search_test.ts  transitive via app/api/app.ts
app/api/votes_test.ts  transitive via app/api/app.ts
app/routes/api/handler_test.ts  transitive via app/routes/api/[...path].ts -> app/api/app.ts
app/routes/index_test.tsx  transitive via app/routes/index.tsx
app/routes/vote_test.ts  transitive via app/routes/ideas/[id]/vote.ts
app/store/ideas_test.ts  transitive via app/store/ideas.ts
app/store/search_test.ts  transitive via app/store/ideas.ts
app/store/votes_test.ts  transitive via app/store/ideas.ts

exit=0
```

## 4. Do spec frontmatter and source headers agree?

The store points at the API spec by both mechanisms. `app/deno.json` has neither a source declaration
nor a `Spec:` header (JSON has no comment header); it is an explicit configuration mapping gap,
not an untested-code finding. The API and runtime prose describe its commands and locked pins.
All application TS/TSX/CSS files, including tests, carry a resolvable owning header.

The first query exposed stale API frontmatter naming `routes/index.tsx`, whose header correctly
names the page spec. The stale API source was removed; the page spec already owns that file.
The gallery lives outside the queried workspace; declaring it as a graph source produced an
unresolved reference. It is now an ordinary link in the architecture spec. The final graph has
zero unresolved headers and zero unresolved frontmatter sources.

[spec-audit.json](spec-audit.json) compares each declared source with actual graph `specifies`
edges and its source header. All 24 declared sources across the four Idea Board specs have the
expected edge. Every declared app file agrees with its owner header; no missing app code header.
The architecture spec's existing query-tool source has a graph edge, without pretending to own
that factory tool's header. Test files point back through headers even when not listed in sources.

```text
$ ../tools/architecture/ask --root codex/workspace spec app/store/ideas.ts
knowledge/components/idea-board-api.md  [both]

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace spec app/deno.json
no spec names app/deno.json, and it names none

exit=0
```

```text
$ ../tools/architecture/ask --root codex/workspace files knowledge/components/idea-board-api.md
app/api/app.ts  [both]
app/api/app_test.ts  [test, Spec: line]
app/api/ideas_test.ts  [test, Spec: line]
app/api/search_test.ts  [test, Spec: line]
app/api/votes_test.ts  [test, Spec: line]
app/client.ts  [both]
app/domain/idea.ts  [both]
app/domain/idea_test.ts  [test, Spec: line]
app/domain/query.ts  [both]
app/main.ts  [both]
app/routes/_app.tsx  [both]
app/routes/api/[...path].ts  [both]
app/routes/api/handler_test.ts  [test, Spec: line]
app/start.ts  [both]
app/store/ideas.ts  [both]
app/store/ideas_test.ts  [test, Spec: line]
app/store/indexes.ts  [both]
app/store/kv.ts  [both]
app/store/search_test.ts  [test, Spec: line]
app/store/votes_test.ts  [test, Spec: line]
app/version.ts  [both]
app/vite.config.ts  [both]

exit=0
```

## 5. Where should a newcomer start?

Start with `app/domain/idea.ts` for the data contract, `app/store/ideas.ts` for persistence and
ordering, and `app/api/app.ts` for the public request boundary; then read `routes/index.tsx` and
its two action islands. These are source-checked entry points, not an instruction to read the
highest-degree file blindly. Global hubs also include factory hook/tool files unrelated to the board.

```text
$ ../tools/architecture/ask --root codex/workspace hubs --top 10
app/store/ideas.ts  used by 12 files, uses 2
.claude/hooks/project_files.py  used by 9 files, uses 0
.claude/hooks/secret_names.py  used by 7 files, uses 0
app/domain/idea.ts  used by 7 files, uses 0
app/api/app.ts  used by 5 files, uses 4
tools/knowledge/doc-links  used by 4 files, uses 2
app/domain/query.ts  used by 4 files, uses 1
tools/architecture/process.py  used by 4 files, uses 0
app/store/kv.ts  used by 3 files, uses 2
tools/context/budget.py  used by 3 files, uses 2

exit=0
```

## Tool scope and final status

The status's code-without-symbol list contains inline `Deno.test` modules, a generated-style
client import and configuration wrappers; this is not a syntax failure. Six inferred call edges
without a connecting import were deliberately dropped. The 31 inventory-only files have no
extractor and must be inspected directly when relevant. No global coverage percentage or
complete runtime call graph is claimed.

```text
$ ../tools/architecture/ask --root codex/workspace status
nodes: 1420
edges: 2512
files: 210
inferred calls dropped (no import between the files): 6
inventory only (no extractor): 31
code files without a symbol: 15
toolchain imports: deno info
unresolved Spec: lines: 0
unresolved frontmatter sources: 0
  no symbol: .claude/hooks/context-budget.py
  no symbol: app/api/app_test.ts
  no symbol: app/api/ideas_test.ts
  no symbol: app/api/search_test.ts
  no symbol: app/api/votes_test.ts
  no symbol: app/client.ts
  no symbol: app/domain/form_test.ts
  no symbol: app/routes/api/handler_test.ts
  no symbol: app/routes/index_test.tsx
  no symbol: app/routes/vote_test.ts
  no symbol: app/store/ideas_test.ts
  no symbol: app/vite.config.ts
  no symbol: tools/agents/run
  no symbol: tools/architecture/setup
  no symbol: tools/context/run

exit=0
```
