# FB-010: the code graph ignores a spec's `sources`, so it cannot say which spec covers a file

- **Severity:** medium (the graph gives a wrong answer to a question the factory's own docs say it answers)
- **Found in:** round 07
- **Status:** applied 10cccce

## Evidence
`tools/architecture/graph.py` builds the graph with Graphify 0.9.95. Markdown is extracted natively:
headings, links, and code names written in the body. A spec's frontmatter `sources` list (each
entry's `resource` is a code path) is the factory's spec ↔ code contract, and it becomes no edge.

On the Idea Board (205 nodes, 363 edges), the question "which spec covers this file?" gave:

| Source | Files linked to a spec |
| --- | --- |
| The graph (body mentions only) | 6 of 23 |
| Each file's `Spec:` header | 18 of 23 |
| `idea-board-runtime.md` in the graph | 0 (its body names commands, not functions) |

Two files are linked to the wrong spec as well: `filters.ts` and `idea.ts` are named in the body of
`idea-board-page.md`, so the graph puts them under both specs. The full table and the script are in
`render/graphify/use-cases.md` (question 4) and `render/graphify/questions.py`, kept in git at
`578f4f3:demo/claude_code/render/`.

## Proposed fix
After extraction, in `tools/architecture/graph.py`:
- read each knowledge doc's frontmatter;
- for each `sources[].resource` that names a file in the manifest, add an edge
  `spec --specifies--> file` with confidence `EXTRACTED` and the frontmatter line as its location;
- report, like the coverage report does, any `resource` that names a file missing from the
  manifest.

Test: in `tools/architecture/tests/`, a fixture spec whose `sources` name two files and whose body
names none; the graph must hold both `specifies` edges and report one missing resource.
