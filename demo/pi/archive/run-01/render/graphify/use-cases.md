# Five questions answered against the built board

Date: 2026-10-29. Round 07 of the Pi run. Every command ran from `demo/` with the parent's
existing installation: `../tools/architecture/ask --root pi/workspace <command>`. No LLM extraction
and no setup command ran. Each command exited 0 and its raw output is kept beside this file in
`raw/`.

The graph covers the whole factory copy (216 files: the app plus the factory's own tools and
hooks), so every count below includes non-app files. That is stated where it matters.

## 1. What breaks if I change the ideas store?

```text
$ ../tools/architecture/ask --root pi/workspace rdeps app/store/ideas.ts --depth 2
app/api/app.ts  [imports at app/api/app.ts:5]  depth 1
app/api/app_test.ts  [imports at app/api/app_test.ts:4]  depth 1
app/api/ideas_test.ts  [imports at app/api/ideas_test.ts:4]  depth 1
app/api/search_test.ts  [imports at app/api/search_test.ts:4]  depth 1
app/api/votes_test.ts  [imports at app/api/votes_test.ts:4]  depth 1
app/routes/ideas/[id]/vote_test.ts  [imports at app/routes/ideas/[id]/vote_test.ts:4]  depth 1
app/routes/index.tsx  [imports at app/routes/index.tsx:7]  depth 1
app/routes/index_test.ts  [imports at app/routes/index_test.ts:5]  depth 1
app/store/database.ts  [imports at app/store/database.ts:2]  depth 1
app/store/ideas_test.ts  [imports at app/store/ideas_test.ts:3]  depth 1
app/store/search_test.ts  [imports at app/store/search_test.ts:3]  depth 1
app/store/votes_test.ts  [imports at app/store/votes_test.ts:3]  depth 1
app/utils.ts  [imports at app/utils.ts:3]  depth 1
app/main.ts  [imports at app/main.ts:4] via app/store/database.ts  depth 2
app/routes/_app.tsx  [imports at app/routes/_app.tsx:2] via app/utils.ts  depth 2
app/routes/api/[...path].ts  [imports at app/routes/api/[...path].ts:3] via app/api/app.ts  depth 2
app/routes/ideas/[id]/vote.ts  [imports at app/routes/ideas/[id]/vote.ts:2] via app/utils.ts  depth 2
```

Thirteen direct dependents and four more at depth 2. Nine of the direct ones are test files, so the
runtime blast radius is four: `app/api/app.ts` (the Hono handlers), `app/routes/index.tsx` (the
page), `app/store/database.ts` (the shared instance) and `app/utils.ts` (the `AppState` type). The
depth-2 list matters: the catch-all route reaches the store through `app/api/app.ts`, and the native
vote route reaches it through the type in `utils.ts`. Change `create`, `get`, `list` or `vote` and
those four are what to read first; imports prove impact, they do not prove every call breaks.

## 2. How does a `POST /api/ideas` reach KV?

The graph shows two file hops:

```text
$ ../tools/architecture/ask --root pi/workspace path 'app/routes/api/[...path].ts' app/store/kv.ts
app/routes/api/[...path].ts -> app/store/database.ts  [imports at app/routes/api/[...path].ts:4]
app/store/database.ts -> app/store/kv.ts  [imports at app/store/database.ts:3]
```

The real call chain has three hops and two of them are invisible to an import graph. Named in full:

1. **HTTP to the Hono app.** `app/routes/api/[...path].ts` exports
   `define.handlers((ctx) => api.fetch(ctx.req))`; Fresh dispatches `/api/*` here, then `api.fetch`
   runs the Hono handler. This is a framework call: no import edge exists beyond the module import,
   and the graph cannot see the dispatch.
2. **The handler to the store.** `createApi` (in `app/api/app.ts`) reads `ctx.state.ideas` — the
   store instance put there by the middleware in `app/main.ts`, which calls `openIdeasKv` and
   `createIdeaStore` through `app/store/database.ts`. The store method (`create`) is a call through
   that runtime instance.
3. **The store to KV.** `create` runs `kv.atomic().set(...).commit()`. The graph reaches
   `app/store/kv.ts` only as a file hop; the call itself is not an edge.

The language server confirms hop 2's shape from both entrances and shows one hop the import graph
does not:

```text
$ ../tools/architecture/ask --root pi/workspace callers app/domain/idea.ts:validateIdea
app/api/app.ts:59  createApi  [lsp call]
app/domain/form_test.ts:27  (module)  [lsp call]
app/domain/form_test.ts:43  (module)  [lsp call]
app/domain/idea_test.ts:6  refusal  [lsp call]
app/domain/idea_test.ts:12  accepted  [lsp call]
app/routes/index.tsx:75  POST  [lsp call]
```

`createIdeaStore` itself is silent:

```text
$ ../tools/architecture/ask --root pi/workspace callers app/store/ideas.ts:createIdeaStore
app/api/app_test.ts:11  withApi  [lsp call]
app/api/ideas_test.ts:11  withApi  [lsp call]
... (seven test helpers, nothing in app/)
```

The reason is in the source: `app/store/database.ts` passes `createIdeaStore` to `.then()`, so it is
never called by name. The graph is right to be silent and must not be read as "unused". The store's
`create` is reached through the imported `IdeaStore` type and the instance in `ctx.state`.

## 3. Which source files have no test pointing at them?

```text
$ ../tools/architecture/ask --root pi/workspace untested
49 source files: 14 imported by a test, 18 named by a test, 5 reached through others, 12 reached by none
untested: app/main.ts
untested: app/routes/api/[...path].ts
untested: app/server.ts
untested: app/store/database.ts
untested: loops/decisions-api/client.sh
untested: tools/claude-plugins/deno-lsp/scripts/deno-lsp
untested: tools/claude-plugins/native-lsp/scripts/jdtls-launch
untested: tools/claude-plugins/native-lsp/scripts/tsc-lsp
untested: tools/factory-tests
untested: tools/git-hooks/pre-commit
untested: tools/git-hooks/pre-push
untested: tools/release/release
```

The four app files are wiring, not rules: `main.ts` builds the app and injects the store,
`server.ts` reads `PORT`, `store/database.ts` memoizes the KV handle, and the catch-all hands
requests to Hono. They are proved by the live smoke test and the container health check, not by a
unit test — say so rather than claiming coverage. The other eight are the factory's own scripts,
which ship with the copy; they are not the app.

"Imported by a test" and "reached through others" are different claims:

```text
$ ../tools/architecture/ask --root pi/workspace tests app/store/ideas.ts
app/api/app_test.ts  direct
app/api/ideas_test.ts  direct
app/api/search_test.ts  direct
app/api/votes_test.ts  direct
app/routes/ideas/[id]/vote_test.ts  direct
app/routes/index_test.ts  direct
app/store/ideas_test.ts  direct
app/store/search_test.ts  direct
app/store/votes_test.ts  direct

$ ../tools/architecture/ask --root pi/workspace tests app/routes/index.tsx
app/routes/index_test.ts  direct
```

## 4. Which spec covers this file, and which file has none?

```text
$ ../tools/architecture/ask --root pi/workspace spec app/routes/index.tsx
knowledge/components/idea-board-api.md  [in the spec's sources; its Spec: line names idea-board-page.md]
knowledge/components/idea-board-page.md  [both]

$ ../tools/architecture/ask --root pi/workspace spec app/store/ideas.ts
knowledge/components/idea-board-api.md  [both]
```

"both" means the file's `Spec:` header names the document *and* the document's frontmatter
`sources` names the file. `app/routes/index.tsx` is the one file with two claims: the page spec owns
its behaviour, the API spec lists it as a source (the page is built from the API's types).

The gap check looks the other way, from a spec to its files:

```text
$ ../tools/architecture/ask --root pi/workspace files knowledge/components/idea-board-api.md
... 25 files ...
app/routes/index.tsx  [in the spec's sources; its Spec: line names idea-board-page.md]
app/vite.config.ts  [Spec: line; missing from the spec's sources (gap)]
```

`app/vite.config.ts` named the API spec in its header but the spec did not list it. Fixed in this
round: the file is now a source of `knowledge/components/idea-board-api.md`. This is the one case
where the graph beat reading the spec: nothing fails when a source is missing, and no test covers
frontmatter.

## 5. Where should a newcomer start?

```text
$ ../tools/architecture/ask --root pi/workspace hubs
app/store/ideas.ts  used by 13 files, uses 3
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

Ignore the factory rows. In the app: `app/store/ideas.ts` (13), `app/domain/idea.ts` (8),
`app/api/app.ts` (5), `app/utils.ts` (5). Read `app/routes/index.tsx` and
`app/routes/api/[...path].ts` first — the two entrances — then `app/api/app.ts`, then the store and
the pure domain modules. `app/main.ts` is short and shows the wiring.

## Where the graph was silent or wrong

| Finding | Evidence | Verdict |
| --- | --- | --- |
| `createIdeaStore` has no call edge | `ask callers` lists only test helpers; `app/store/database.ts` passes it to `.then()` | Silent, and correct: a function passed by value has no call site |
| `main.ts` injects the store into `ctx.state` | `ask path` stops at `store/kv.ts`; no edge from `database.ts` to the Hono handlers | Silent: the binding is runtime state, not an import |
| `app/vite.config.ts` missing from its spec | `ask files knowledge/components/idea-board-api.md` prints `(gap)` | A real gap, found by the graph, fixed this round |
| "Untested" mixes app and factory | 12 files, only 4 in `app/` | Read the list, not the count: the copy ships the factory |
| 10 code files without a symbol | `ask status` lists `client.ts`, `vite.config.ts`, test files | Inventory only: no symbols is not no use |
| Counts are factory-wide | `ask status`: 1516 nodes, 2679 edges, 216 files | Never read "used by N" as app-only |

Every claim above was checked against source before being written down; the graph is a map, not a
verdict.
