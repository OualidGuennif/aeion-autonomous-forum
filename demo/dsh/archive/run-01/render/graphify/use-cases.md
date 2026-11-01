# Graphify use cases — the Idea Board

Five questions a developer really asks about this app, each answered by
`tools/architecture/ask` from the factory root, against the workspace copy. Every command below was
run in this round; the output is copied verbatim.

```bash
cd /Users/wallseven/Desktop/aeion-factory
tools/architecture/ask --root demo/dsh/workspace <question>
```

The graph is structural only: it is built from imports, `Spec:` lines and frontmatter `sources`, with
type-resolved callers from the project's own Deno language server. No language model is involved. The
last build had **1,520 nodes and 2,667 edges over 214 files**, with 0 unresolved `Spec:` lines and 0
unresolved frontmatter sources:

```text
$ tools/architecture/ask --root demo/dsh/workspace status
nodes: 1520
edges: 2667
files: 214
inferred calls dropped (no import between the files): 4
inventory only (no extractor): 31
code files without a symbol: 8
toolchain imports: deno info
unresolved Spec: lines: 0
unresolved frontmatter sources: 0
  no symbol: .claude/hooks/context-budget.py
  no symbol: app/client.ts
  no symbol: app/domain/form_test.ts
  no symbol: app/routes/_app.tsx
  no symbol: app/vite.config.ts
  no symbol: tools/agents/run
  no symbol: tools/architecture/setup
  no symbol: tools/context/run
```

---

## 1. What breaks if I change the ideas store?

```bash
$ tools/architecture/ask --root demo/dsh/workspace rdeps app/store/ideas.ts --depth 2
app/api/app.ts  [imports at app/api/app.ts:6]  depth 1
app/api/app_test.ts  [imports at app/api/app_test.ts:4]  depth 1
app/api/ideas_test.ts  [imports at app/api/ideas_test.ts:5]  depth 1
app/api/search_test.ts  [imports at app/api/search_test.ts:5]  depth 1
app/api/votes_test.ts  [imports at app/api/votes_test.ts:5]  depth 1
app/domain/filters.ts  [imports at app/domain/filters.ts:3]  depth 1
app/routes/index.tsx  [imports at app/routes/index.tsx:7]  depth 1
app/routes/index_test.ts  [imports at app/routes/index_test.ts:5]  depth 1
app/store/database.ts  [imports at app/store/database.ts:3]  depth 1
app/store/ideas_test.ts  [imports at app/store/ideas_test.ts:3]  depth 1
app/store/search_test.ts  [imports at app/store/search_test.ts:3]  depth 1
app/store/votes_test.ts  [imports at app/store/votes_test.ts:3]  depth 1
app/utils.ts  [imports at app/utils.ts:3]  depth 1
app/main.ts  [imports at app/main.ts:4] via app/store/database.ts  depth 2
app/routes/_app.tsx  [imports at app/routes/_app.tsx:2] via app/utils.ts  depth 2
app/routes/api/[...path].ts  [imports at app/routes/api/[...path].ts:3] via app/api/app.ts  depth 2
app/routes/ideas/[id]/vote.ts  [imports at app/routes/ideas/[id]/vote.ts:2] via app/utils.ts  depth 2
```

**Reading it.** Thirteen files import the store at depth 1: the API, the page, the database module,
the two typed-route helpers, and eight test files. Everything else is depth 2 and reaches it through
the API or the route helpers. So a change to `IdeaStore` is felt first in `app/api/app.ts` and
`app/routes/index.tsx`; the eight test files are the safety net, not extra consumers.

**Where it is silent.** `app/domain/filters.ts` appears only because it imports the `IdeaFilter`
type; it never calls the store. The graph cannot say that, and a reader must open the file to see it.

## 2. How does `POST /api/ideas` reach KV?

```bash
$ tools/architecture/ask --root demo/dsh/workspace path 'app/routes/api/[...path].ts' app/store/ideas.ts
app/routes/api/[...path].ts -> app/api/app.ts  [imports at app/routes/api/[...path].ts:3]
app/api/app.ts -> app/store/ideas.ts  [imports at app/api/app.ts:6]
```

The file graph gives two hops. The real request has more, so the language server is asked for the
method call the imports cannot show:

```bash
$ tools/architecture/ask --root demo/dsh/workspace callers app/store/ideas.ts:create
'create' names 2 symbols in app/store/ideas.ts; give one as app/store/ideas.ts:<line>:
  app/store/ideas.ts:20  IdeaStore.create
  app/store/ideas.ts:166  createIdeaStore.create

$ tools/architecture/ask --root demo/dsh/workspace callers app/store/ideas.ts:20
app/api/app.ts:69  createApi  [lsp call]
app/routes/index.tsx:109  POST  [lsp call]
app/routes/index_test.ts:60  (module)  [lsp call]
...
```

Every hop, named:

1. `POST /api/ideas` is matched by Fresh's catch-all `app/routes/api/[...path].ts`, which calls
   `api.fetch(ctx.req)` (the route imports `app/api/app.ts`).
2. `app/api/app.ts` reads and parses the body, then calls `validateIdea` from `app/domain/idea.ts`
   (a sibling import, depth 1 in `deps`, not on this path).
3. It then calls `deps.ideas.create(result.value)` at `app/api/app.ts:69` — the interface method the
   language server resolves to `IdeaStore.create` at `app/store/ideas.ts:20`.
4. `createIdeaStore`'s implementation (`app/store/ideas.ts:166`) builds one `kv.atomic()` operation
   and commits it: `["ideas", id]`, `["ideas_by_votes", 0, id]`, and one `["ideas_by_tag", tag, id]`
   per tag.
5. Deno KV writes the commit; the response is `201 {"idea": …}`.

**Where the graph is silent.** It has no edge for hop 3: `deps.ideas.create` is a method on an
interface, not an import, so only `callers …:20` (the language server) shows it. It also has no edge
for hop 4: `kv.atomic()` is a runtime API, not a project symbol.

## 3. Which source files have no test file pointing at them?

```bash
$ tools/architecture/ask --root demo/dsh/workspace untested
47 source files: 11 imported by a test, 19 named by a test, 6 reached through others, 11 reached by none
untested: app/main.ts
untested: app/routes/ideas/[id]/vote.ts
untested: app/server.ts
untested: loops/decisions-api/client.sh
untested: tools/claude-plugins/deno-lsp/scripts/deno-lsp
untested: tools/claude-plugins/native-lsp/scripts/jdtls-launch
untested: tools/claude-plugins/native-lsp/scripts/tsc-lsp
untested: tools/factory-tests
untested: tools/git-hooks/pre-commit
untested: tools/git-hooks/pre-push
untested: tools/release/release
```

**The distinction that matters.** The first line separates four cases, and they are not the same
claim:

- *imported by a test* (11): the test file names the module directly, so a rename breaks the test at
  compile time.
- *named by a test* (19): the test mentions the file's symbol but does not import the file; weaker.
- *reached through others* (6): no test points at the file, but a test reaches it through another
  module. This is coverage by path, not by contract.
- *reached by none* (11): the list below. For the app that is `app/main.ts`, `app/server.ts` and
  `app/routes/ideas/[id]/vote.ts`.

`app/routes/ideas/[id]/vote.ts` is the honest gap: it is exercised only by the round 04 curl, never by
a test. `app/main.ts` and `app/server.ts` are the process entry points; they are started, not imported,
so the graph will always report them.

**Where the graph is silent.** It cannot see a test that runs the built app over HTTP (the round 04
and 06 curls), so "reached by none" means "no test file reaches it", not "untested in practice".

## 4. Which spec covers this file, and which file has none?

```bash
$ tools/architecture/ask --root demo/dsh/workspace spec app/store/ideas.ts
knowledge/components/idea-board-api.md  [both]

$ tools/architecture/ask --root demo/dsh/workspace spec app/domain/filters.ts
knowledge/components/idea-board-api.md  [both]
knowledge/components/idea-board-page.md  [in the spec's sources; its Spec: line names idea-board-api.md]
```

`[both]` means the file's `Spec:` line names the doc **and** the doc's frontmatter `sources` names the
file — the two directions agree. The second line is the honest asymmetry: `filters.ts` is shared, its
`Spec:` line can name only one doc, and the page spec lists it as a source because the page uses it.

Before this round, `ask files knowledge/components/idea-board-api.md` reported six gaps:

```text
app/client.ts  [Spec: line; missing from the spec's sources (gap)]
app/main.ts  [Spec: line; missing from the spec's sources (gap)]
app/routes/_app.tsx  [Spec: line; missing from the spec's sources (gap)]
app/server.ts  [Spec: line; missing from the spec's sources (gap)]
app/utils.ts  [Spec: line; missing from the spec's sources (gap)]
app/vite.config.ts  [Spec: line; missing from the spec's sources (gap)]
```

They were the Fresh wiring files, all pointing at the **API** spec, which never listed them. They are
not the API; they are how the app runs. This round pointed their `Spec:` lines at
`knowledge/components/idea-board-runtime.md` and added them to that spec's `sources`. The check now
reads:

```text
$ tools/architecture/ask --root demo/dsh/workspace files knowledge/components/idea-board-runtime.md
app/.dockerignore  [both]
app/Dockerfile  [both]
app/client.ts  [both]
app/compose.yaml  [both]
app/main.ts  [both]
app/routes/_app.tsx  [both]
app/server.ts  [both]
app/store/database.ts  [in the spec's sources; its Spec: line names idea-board-api.md]
app/utils.ts  [both]
app/vite.config.ts  [both]
```

The one remaining note is the same shared-module asymmetry as `filters.ts`: `database.ts` is listed by
the runtime spec but its `Spec:` line names the API spec, which owns the KV contract. No file is left
with a `Spec:` line and no spec.

**A trap worth knowing.** `ask` rebuilt the graph after the edit (new timestamp) but kept the old
frontmatter, so the gaps persisted and looked authoritative. Only `rm -rf
demo/dsh/workspace/.aeion/graph` followed by `ask status` produced the new edges (2,662 → 2,667).
Filed as FB-009.

## 5. Where should a newcomer start?

```bash
$ tools/architecture/ask --root demo/dsh/workspace hubs
app/store/ideas.ts  used by 13 files, uses 1
.claude/hooks/project_files.py  used by 9 files, uses 0
app/domain/idea.ts  used by 8 files, uses 0
.claude/hooks/secret_names.py  used by 7 files, uses 0
app/api/app.ts  used by 5 files, uses 4
app/utils.ts  used by 5 files, uses 1
tools/knowledge/doc-links  used by 4 files, uses 2
tools/agents/hook.py  used by 4 files, uses 1
tools/architecture/process.py  used by 4 files, uses 0
tools/context/budget.py  used by 3 files, uses 2
```

**Reading it.** For the app, read `app/store/ideas.ts` first (13 dependants: it owns every KV key and
transaction), then `app/domain/idea.ts` (the one validator), then `app/api/app.ts` (the contract that
uses both). The rest of the list is factory machinery, not the app. A newcomer can stop at the first
three app rows and read the two component specs beside them.

**Where it is silent.** "Used by" counts imports, not importance: `app/utils.ts` is a five-line
`define` helper and ranks beside `app/api/app.ts`. The graph tells you what is connected, not what is
worth reading first — that is the specs' job.

---

## What the graph was wrong or silent about

1. **The brief's path does not exist.** `plan/07-architecture.md` asks for `app/store/kv.ts`; the
   store is `app/store/ideas.ts`. The commands above use the real path.
2. **No edge for an interface method call.** `deps.ideas.create(...)` at `app/api/app.ts:69` is
   invisible to the import graph; `callers …:20` (language server) is the only way to see it.
3. **No edge for a runtime API.** `kv.atomic()`, `Deno.serve` and `fetch` are not project symbols, so
   the last hops of questions 2 and 5 are read from the code, not the graph.
4. **Stale frontmatter after a rebuild.** See question 4 and FB-009: the first rebuild kept the old
   `sources`, and only deleting `.aeion/graph` fixed it.
5. **"No symbol" is not "no file".** Eight files, including `app/client.ts`, `app/routes/_app.tsx` and
   `app/vite.config.ts`, have no extractable symbol. `find` cannot answer for them; `spec` can.
6. **`untested` means "no test file reaches it".** `app/routes/ideas/[id]/vote.ts` is covered by a
   curl, which the graph cannot see.
