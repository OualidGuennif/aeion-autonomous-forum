# Round 05 — Tags filters and bounded search

Date: 2026-10-28. All rounds authorized by the owner together.

## Plan and implementation

Plan: atomically index tags and add shared tag/text query parsing, literal substring search and page filters. Red tests preceded the implementation. Search considers only the 500 newest ideas globally, including tag+text with vote sort; a 501-record test proves the cutoff. A KV proxy verifies tag-only reads use the tag index rather than scanning ideas. Tag/text combine with both sorts; malformed tags and long q are refused. Whitespace q clears the filter. Page tests prove escaped filter text and native vote redirects retain filters. Chrome passed four viewport/theme checks and island creation/voting; browser.json and captures are retained. Both production and Vite development smoke checks pass (development output is in ../05-dev/). No full-text engine, extra index or client routing.

## Validation

Acceptance commands were run from workspace/app; factory checks from workspace.

- [fmt](evidence/05/fmt.log)
- [lint](evidence/05/lint.log)
- [check](evidence/05/check.log)
- [test](evidence/05/test.log)
- [build](evidence/05/build.log)
- [knowledge-check](evidence/05/knowledge-check.log)
- [doc-links](evidence/05/doc-links.log)
- [repo-health](evidence/05/repo-health.log)
- [smoke](evidence/05/smoke.log)

[Source checksums](evidence/05/source-sha256.txt) identify the local, git-ignored application.
No base-factory change, global tool install or push.

```text
Result:          done
Tests:           49 passed | 0 failed; production smoke PASS
Factory checks:  knowledge-check, doc-links, repo-health: PASS
Guard refusals:  0 during this round
Human help:      0 additional owner actions; rounds 01–07 already authorized
Feedback:        none new
Elapsed:         3 minutes (rounded, first retained evidence through sign-off)
```
