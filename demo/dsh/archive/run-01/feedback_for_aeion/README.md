# Feedback for Æion Factory — dsh run

One file per friction, numbered from 001 for this run. Each has severity, evidence with the real
output, a proposed fix and its status. Statuses: `open`, or `applied <sha>` once the base factory
carries the fix (the base is fixed there, never from this copy).

| FB | Title | Severity | Status |
| --- | --- | --- | --- |
| [001](FB-001-dsh-drops-session-start-context.md) | dsh drops the SessionStart context, so the checkpoint rule never reaches the model | medium | applied c3f586f |
| [002](FB-002-dsh-no-stop-sign-off.md) | dsh never sends Stop, so the sign-off hook never runs | medium | applied c3f586f |
| [003](FB-003-headless-commit-sandbox.md) | headless dsh cannot commit from `demo/`, and the brief says the sandbox asks | medium | applied (run 2 brief) |
| [004](FB-004-deno-install-before-check.md) | the scaffold's acceptance commands fail until `deno install` | low | applied (run 2 brief) |
| [005](FB-005-concurrent-votes-vs-retry-cap.md) | round 04 asks for 25 concurrent votes and a 10-retry cap that cannot keep them | medium | applied (run 2 brief) |
| [006](FB-006-handover-outside-the-sandbox.md) | the 250k closure needs a handover file the dsh sandbox refuses to write | high | documented c3f586f (launch at the root, or the driver writes closure files) |
| [007](FB-007-live-log-invalidates-the-receipt.md) | a live log in the session workspace makes every checkpoint receipt stale | medium | applied c3f586f |
| [008](FB-008-docker-config-outside-the-sandbox.md) | the sandbox blocks `~/.docker`, so the brief's own `docker compose up` fails | medium | documented c3f586f |
| [009](FB-009-ask-keeps-stale-frontmatter.md) | `ask` rebuilds the graph but keeps stale spec frontmatter, so a gap looks authoritative | medium | applied c3f586f |
