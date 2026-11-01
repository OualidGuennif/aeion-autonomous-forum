# Feedback for Æion Factory — Pi run

One file per friction, numbered from 001. Each has severity, evidence with the real output, a
proposed fix and its status. Statuses: `open`, or `applied <sha>` once the base factory carries the
fix (the base is fixed there, never from this copy).

| FB | Title | Severity | Status |
| --- | --- | --- | --- |
| [001](FB-001-task-entry-shape-undocumented.md) | the `work/current.md` entry shape is enforced but documented nowhere | low | applied c3f586f |
| [002](FB-002-handover-headings-undocumented.md) | a verified checkpoint requires handover headings that nothing states | low | applied c3f586f |
| [003](FB-003-serverless-single-file-gallery.md) | ship a serverless single-file gallery for diagrams (requested by the owner) | medium | applied (`tools/architecture/gallery`) |
| [004](FB-004-round-07-observations.md) | round 07: Pi has no Stop hook, the brief predates `finalize`, geometry needs an iteration budget, HTML should be untracked, `doc-links` misses spec/source gaps | low | applied (run 2 brief) |
