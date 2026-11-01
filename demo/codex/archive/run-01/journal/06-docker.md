# Round 06 — Docker runtime and persistent volume

Date: 2026-10-28. All rounds authorized by the owner together.

## Plan and implementation

Plan: build once from the official denoland/deno:2.9.8 image, freeze dependencies, run as deno, add a Deno healthcheck and named KV volume. Missing Compose config was observed failing before implementation. Docker acceptance output proves healthy status, a preserved idea after down/up, UID 1993 and health in 5.47 seconds after restart. A negative build with an isolated temporary dependency drift failed specifically because the lockfile was out of date; the actual app stayed unchanged. Native Deno audit found no known vulnerabilities; security-review.md records the separate manual review and limits. The exact image identity is retained. Test container, network and volume were removed; no registry push. FB-002 records a documentation-heredoc guard false positive; apply_patch was the safe workaround. The local runtime and all app/factory gates pass.

## Validation

Acceptance commands were run from workspace/app; factory checks from workspace.

- [fmt](evidence/06/fmt.log)
- [lint](evidence/06/lint.log)
- [check](evidence/06/check.log)
- [test](evidence/06/test.log)
- [build](evidence/06/build.log)
- [knowledge-check](evidence/06/knowledge-check.log)
- [doc-links](evidence/06/doc-links.log)
- [repo-health](evidence/06/repo-health.log)

- [Docker persistence and non-root readback](evidence/06/container-acceptance.log)
- [Frozen-lock negative build](evidence/06/frozen-lock-negative.log)
- [Dependency audit](evidence/06/dependency-audit.log)
- [Security review and limits](evidence/06/security-review.md)

[Source checksums](evidence/06/source-sha256.txt) identify the local, git-ignored application.
No base-factory change, global tool install or push.

```text
Result:          done
Tests:           49 passed | 0 failed; production smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  1 documentation false positive; see FB-002
Human help:      0 additional owner actions; rounds 01–07 already authorized
Feedback:        FB-002 new
Elapsed:         3 minutes (rounded, first retained evidence through sign-off)
```
