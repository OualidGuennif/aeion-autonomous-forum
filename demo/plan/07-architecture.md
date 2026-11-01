# 07 — Architecture: Graphify use cases and an Archify gallery

## Goal
Make the finished app understandable at a glance, and show what a code graph is actually good for.

## Part A — Graphify (structural graph of the app)
Follow the skill `graph` (`.agents/skills/graph/SKILL.md`): `tools/architecture/ask` keeps a graph of
the workspace and answers exactly (it does not need git; `--root <workspace>` from the factory root).
Answer real questions with it, and keep each command and its real output in `graphify/use-cases.md`:

| Question a developer really asks | How |
| --- | --- |
| What breaks if I change the ideas store? | `ask rdeps app/store/ideas.ts --depth 2` |
| How does a `POST /api/ideas` reach KV? | `ask path "<catch-all route>" app/store/kv.ts`, then the language server for method calls |
| Which source files have no test file pointing at them? | `ask untested`, `ask tests <file>` |
| Which spec covers this file, and which file has none? | `ask spec <file>`, `ask files <spec>` |
| Where should a newcomer start? | `ask hubs` |

The graph is structural only: check every answer against the code before trusting it, and say where
the graph was wrong or silent. The checks that tell a good answer from a lazy one:
- Question 2: name every hop, including the store method the API calls; if the graph has no edge
  for a hop, say so instead of skipping it.
- Question 3: distinguish "a test imports it" from "a test reaches it through other files".
- Question 4: compare the graph's spec links with each file's `Spec:` header line; the frontmatter
  `sources` of each spec should appear as `specifies` edges.

## Part B — Archify gallery (many small diagrams of the whole program)
One page `index.html` with a card per diagram: title, the question it answers, a live thumbnail,
a link to the full diagram. Each diagram is small (at most about 10 boxes) and answers one question.

| Family | Diagrams |
| --- | --- |
| System | system overview (browser, Fresh server, islands, Hono API, domain, store, KV); Docker deployment |
| Sequence | page load (SSR); create an idea (island → API → validation → KV); vote (optimistic transaction with retry); filter and search; health check |
| Use case | Visitor; Contributor; Operator (actors outside, use cases inside the system boundary) |
| Other | idea lifecycle; KV key model; request data flow; the Æion round that built it |

Every diagram passes `archify finalize --browser-check` (validation, render and browser check, with a receipt).
Expect two to four `finalize` rounds per diagram: apply its `supportedFixes` and rerun once per change,
do not rewrite the JSON. Archify has no "use case" type: draw use cases as an `architecture` diagram (actors as external components, use cases as
components inside a boundary) and say so on the card.

## Acceptance checks
```bash
<factory>/tools/architecture/ask --root <workspace> status
node <archify>/bin/archify.mjs validate <type> diagrams/<name>.json --quality showcase   # quick gate
node <archify>/bin/archify.mjs finalize <type> diagrams/<name>.json diagrams/<name>.html --browser-check --quality showcase
<factory>/tools/architecture/gallery render --title "Idea Board"   # one index.html, opens from file://
```

`finalize --out-dir <dir>` keeps its receipts out of `diagrams/`. `tools/architecture/refresh`
needs a git repository and refuses a plain folder: run `finalize` per diagram. `archify examples` is not
a listing: it rewrites the example HTML inside the Archify install; read its example JSON files instead.

Commit the diagram JSON (and `catalog.json` if you write one), never the HTML: the gallery and the
diagrams are generated, and the parent repository ignores them.

## Out of scope
Archify evidence links (`sources`): they need a git revision and a repository URL, which a plain
folder does not have. LLM-inferred graph edges (the Bash guard refuses Graphify's LLM commands).

## Expected result
`render/index.html`, built by `tools/architecture/gallery`, opens from `file://` with no server, every
card loads its diagram (open it in a browser and say so in the journal), and `graphify/use-cases.md`
answers the five questions with real output.
