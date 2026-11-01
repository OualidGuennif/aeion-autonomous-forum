# FB-001 — `ask` incremental rebuild missed a frontmatter-only spec edit

- Severity: low (wrong answer, easily forced)
- Date: 2026-10-28
- Harness: OpenCode, demo run round 07

## What happened

1. `ask --root opencode/workspace spec app/domain/search.ts` reported
   `[Spec: line; missing from the spec's sources (gap)]`.
2. I added the missing `sources` entries to `knowledge/components/idea-board-api.md`
   (`app/domain/search.ts`, the two search test files).
3. Re-running `ask files knowledge/components/idea-board-api.md` and `ask spec
   app/domain/search.ts` still reported the gap, and `ask status` still showed the old counts
   (1415 nodes, 2470 edges) although the doc had changed size and mtime.
4. Deleting `opencode/workspace/.aeion/graph/` and re-running `status` produced 1415 nodes,
   **2473** edges, and `[both]` for `app/domain/search.ts` — the truth.

## Evidence

- `demo/opencode/render/graphify/specs-after-fix.txt` — stale answer after the edit.
- `demo/opencode/render/graphify/specs.txt` — the original gap.
- The forced rebuild (no file kept; the command is `rm -rf .aeion/graph` in the workspace, then any
  `ask` query) closes it immediately.

## Proposed fix

Invalidate the freshness fingerprint for spec documents on content, not only on size and mtime, or
key Graphify's per-file extraction cache on the file hash; today a same-size, same-second edit can
survive a rebuild. A cheaper guard: when `ask`'s incremental build runs, compare the fingerprint of
`knowledge/**/*.md` frontmatter too (a hash of the frontmatter block per doc).

## Status

Applied c3f586f.
