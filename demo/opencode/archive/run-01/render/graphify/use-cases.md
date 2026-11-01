# Graphify use cases — the Idea Board app

Graph of `demo/opencode/workspace` (207 files, 1415 nodes, 2470 edges at the last refresh), built by
the parent's `tools/architecture/ask --root opencode/workspace`; the copy's own Graphify venv was
never installed. Raw output is beside this file (`status.txt`, `rdeps.txt`, `path.txt`, `tests.txt`,
`specs.txt`, `hubs.txt`).

## 1. What breaks if I change the ideas store?

```bash
$ ../tools/architecture/ask --root opencode/workspace rdeps app/store/ideas.ts --depth 2
app/api/ideas.ts  [imports at app/api/ideas.ts:6]  depth 1
app/api/ideas_test.ts  [imports at app/api/ideas_test.ts:4]  depth 1
app/api/votes_test.ts  [imports at app/api/votes_test.ts:4]  depth 1
app/page_vote.ts  [imports at app/page_vote.ts:4]  depth 1
app/routes/index.tsx  [imports at app/routes/index.tsx:9]  depth 1
app/routes/index_test.ts  [imports at app/routes/index_test.ts:4]  depth 1
app/store/ideas_test.ts  [imports at app/store/ideas_test.ts:3]  depth 1
app/store/search_test.ts  [imports at app/store/search_test.ts:3]  depth 1
app/store/votes_test.ts  [imports at app/store/votes_test.ts:3]  depth 1
app/api/app.ts  [imports at app/api/app.ts:5] via app/api/ideas.ts  depth 2
app/routes/ideas/[id]/vote.ts  [imports at app/routes/ideas/[id]/vote.ts:4] via app/page_vote.ts  depth 2
```

Verified against the code: `app/api/ideas.ts:6` imports `createIdeaStore`, `app/page_vote.ts:4`
too, `app/routes/index.tsx:9` imports the store types (and `handleGet` receives a store). The graph
misses one edge by design: `app/routes/api/[...path].ts` reaches the store through
`createApi({ kv })` → `ideaRoutes` → `createIdeaStore` — imports connect the modules
(`[...path].ts` → `app/api/app.ts` → `app/api/ideas.ts`), and the graph does resolve that at depth 2
from the route file, but not from the store file upward (rdeps only follow imports the other way).

## 2. How does a `POST /api/ideas` reach KV?

```bash
$ ../tools/architecture/ask --root opencode/workspace path "app/routes/api/[...path].ts" app/store/kv.ts
app/routes/api/[...path].ts -> app/store/kv.ts  [imports at app/routes/api/[...path].ts:4]

$ ../tools/architecture/ask --root opencode/workspace path app/api/ideas.ts app/store/ideas.ts
app/api/ideas.ts -> app/store/ideas.ts  [imports at app/api/ideas.ts:6]
```

The graph gives two import hops; the request path has more hops, and the method calls between them
have no edges because they are methods on an injected `Deno.Kv`/store object. Named from the code
(each line opened):

1. `routes/api/[...path].ts:10` — the catch-all `define.handlers` passes `context.req` to
   `api.fetch`.
2. `api/app.ts:21` — `ideaRoutes(app, deps)` mounts the routes with `deps.kv`.
3. `api/ideas.ts:24` — `createIdeaStore(deps.kv)`; `POST /ideas` reads the body
   (`api/ideas.ts:29`), validates with `validateIdeaInput` (`:48`), then `store.create`.
4. `store/ideas.ts:31` — `kv.atomic().set(["ideas", id], idea)` (+ the vote and tag index keys),
   `commit()` at `:46`.

The graph is silent on hops 3–4's method calls (no edge for `store.create` / `kv.atomic`);
`ask callers app/store/ideas.ts:create` would answer that with the Deno language server, which this
run did not start.

## 3. Which source files have no test pointing at them?

```bash
$ ../tools/architecture/ask --root opencode/workspace untested
47 source files: 9 imported by a test, 18 named by a test, 8 reached through others, 12 reached by none
untested: app/main.ts
untested: app/routes/api/[...path].ts
untested: app/routes/ideas/[id]/vote.ts
untested: app/vite.config.ts
... (factory tooling, unrelated to the app)
```

Honest reading: "a test imports it" is `direct`; "a test names it" is a script or string match;
"reached through others" is transitive. Among the four app files: `app/main.ts` (the Fresh app
assembly) and `app/vite.config.ts` are exercised only by the build and the HTTP smoke;
`routes/api/[...path].ts` and `routes/ideas/[id]/vote.ts` are exercised only through the running
server (the round 01, 02, 04 smokes and container checks). The tests that cover the store:

```bash
$ ../tools/architecture/ask --root opencode/workspace tests app/store/ideas.ts
app/api/ideas_test.ts  direct
app/api/votes_test.ts  direct
app/routes/index_test.ts  direct
app/store/ideas_test.ts  direct
app/store/search_test.ts  direct
app/store/votes_test.ts  direct
app/api/app_test.ts  transitive via app/api/app.ts -> app/api/ideas.ts
app/api/search_test.ts  transitive via app/api/app.ts -> app/api/ideas.ts
```

## 4. Which spec covers this file, and which file has none?

```bash
$ ../tools/architecture/ask --root opencode/workspace spec app/store/ideas.ts
knowledge/components/idea-board-api.md  [both]

$ ../tools/architecture/ask --root opencode/workspace files knowledge/components/idea-board-api.md
app/api/app.ts  [both]
app/api/ideas.ts  [both]
app/domain/idea.ts  [both]
app/domain/search.ts  [Spec: line; missing from the spec's sources (gap)]
app/store/ideas.ts  [both]
app/store/kv.ts  [both]
app/routes/api/[...path].ts  [both]
app/version.ts  [both]
... tests [both], plus search tests [test, Spec: line]
```

The gap was real and fixed in this round: `app/domain/search.ts` carried its `Spec:` line but was
missing from the API spec's `sources` frontmatter; the search test files too. All three were added,
and `ask files` then reports `both` for them. The page spec covers `routes/index.tsx`,
`islands/VoteButton.tsx`, `routes/ideas/[id]/vote.ts`, `page_vote.ts` and `domain/form.ts` — the
graph reported `[both]` for `routes/index.tsx` and the island.

## 5. Where should a newcomer start?

```bash
$ ../tools/architecture/ask --root opencode/workspace hubs --top 10
app/store/ideas.ts  used by 9 files, uses 2
.claude/hooks/project_files.py  used by 9 files, uses 0
.claude/hooks/secret_names.py  used by 7 files, uses 0
app/api/app.ts  used by 6 files, uses 2
app/domain/idea.ts  used by 5 files, uses 0
app/utils.ts  used by 5 files, uses 0
tools/knowledge/doc-links  used by 4 files, uses 2
...
```

For the app: `app/store/ideas.ts` is the hub (nine users: the API, both page flows, six test
files), then `app/api/app.ts` (the Hono composition) and `app/domain/idea.ts` (the pure rules).
Reading those three explains the program; the routes and islands are thin around them.

## Where the graph was wrong or silent

- No edge for method calls on injected objects (`store.create`, `kv.atomic`, `store.list`): named
  in question 2 by reading the lines.
- No reverse edge `store/ideas.ts` ← `routes/api/[...path].ts` at depth 1 (the path is
  `[...path].ts → app.ts → ideas.ts`), which is why question 1 lists the route only at depth 2 via
  `page_vote.ts`.
- The `Spec:` gap in question 4: found by the graph itself, fixed in this round.
- Nothing else was contradicted by the code; each claim above cites a `file:line` that was opened.
