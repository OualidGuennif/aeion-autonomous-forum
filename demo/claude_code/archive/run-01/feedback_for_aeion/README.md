# Feedback for Æion Factory from the Claude Code run

Each file covers one friction with the factory. It gives the evidence, a proposed fix and a status.
`applied <sha>` means the base factory was fixed on `develop` in that commit, with a test.

| FB | Round | Severity | What went wrong | Status |
| --- | --- | --- | --- | --- |
| [001](FB-001-archify-evidence-needs-git.md) | 07 | low | Archify evidence links need a git SHA and a repository URL | open (upstream) |
| [002](FB-002-refresh-nested-plain-folder.md) | 00 | medium | `refresh` analysed the parent repository from a nested plain folder | applied `aeda5e0` |
| [003](FB-003-copy-inherits-factory-state.md) | 01 | low | a new project inherits the factory's own task state and history | applied (`release --new-project`) |
| [004](FB-004-agents-md-at-cap.md) | 01 | medium | `AGENTS.md` shipped at its line cap but asks you to add lines | applied `be08858` |
| [005](FB-005-plain-walk-ignores-gitignore.md) | 01 | medium | the plain-folder walk counted build output `.gitignore` excludes | applied `1c9b82c` |
| [006](FB-006-parent-doc-links-nested-project.md) | 01 | high | the parent's `doc-links` checked a nested project's `Spec:` lines | applied `2c2fb1d` |
| [007](FB-007-closure-blocks-checkpoint.md) | 03 | medium | at the 250k alert, the checkpoint could not run from a subfolder | applied `87a27b0` |
| [008](FB-008-bounded-intake-variable-path.md) | 03 | low | bounded intake called a one-line read through a variable "a dump" | applied `9d4f78c` |
| [009](FB-009-secret-guard-kv-entry-key.md) | 04 | medium | the secret guard read Deno KV's entry accessor as a key file | applied `9d4f78c` |
| [010](FB-010-graphify-ignores-spec-sources.md) | 07 | medium | the code graph ignored a spec's `sources` | applied `10cccce` |

Still open after the fixes:
- FB-001 and FB-003.
- The gaps listed at the end of FB-009.
- The "native usage temporarily unavailable" refusal right after compaction (FB-007).
