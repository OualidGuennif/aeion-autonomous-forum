# FB-009: `ask` rebuilds the graph but keeps stale spec frontmatter, so a gap looks authoritative

- **Severity:** medium (the round's whole point is trusting `ask`; a stale answer is worse than none)
- **Found in:** round 07
- **Status:** applied c3f586f

## Evidence

After pointing six `Spec:` lines at `knowledge/components/idea-board-runtime.md` and adding those
files to that spec's `sources`, `ask` rebuilt the graph (new `graph.json`, new fingerprint) but still
reported every one of them as a gap:

```text
$ tools/architecture/ask --root demo/dsh/workspace spec app/main.ts
knowledge/components/idea-board-runtime.md  [Spec: line; missing from the spec's sources (gap)]
```

The graph's own page node still carried the **old** `sources` list:

```json
{"id": "dockerfile", ...}, {"id": "compose", ...}, {"id": "ignore", ...},
{"id": "start", ...}, {"id": "database", ...}
```

— five entries, while the file on disk had eleven. `ask status` also printed the old edge count
(2,662). Deleting the graph and rebuilding produced the correct edges:

```text
$ rm -rf demo/dsh/workspace/.aeion/graph
$ tools/architecture/ask --root demo/dsh/workspace status
edges: 2667
$ tools/architecture/ask --root demo/dsh/workspace spec app/main.ts
knowledge/components/idea-board-runtime.md  [both]
```

The `specifies` edges went from 5 to 10 for that doc. So the fingerprint-triggered rebuild reused a
cached markdown extraction: the timestamp and fingerprint were fresh, the content was not.

## Proposed fix

1. **Key the extraction cache on the file's content hash**, not on the graph fingerprint, or invalidate
   the cached page node when the file's `mtime_ns`/size changed.
2. Or **rebuild without the extraction cache** when the fingerprint changed (a full build is already
   what the fingerprint promises).
3. Or, at minimum, **say in `status` when a rebuild reused cached extractions**, so an agent knows to
   delete `.aeion/graph` before trusting a spec answer.

Whichever is chosen, `ask` should never print "missing from the spec's sources" for a `sources` entry
it has just been given.
