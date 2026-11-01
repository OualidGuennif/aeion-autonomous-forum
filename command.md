# Commands

Everything you can run here, grouped by loop. Skills are invoked by name in a Claude Code session
(`/plan`, or "run the skill plan"); tools and loops from a shell.

## Skills (`.claude/skills/`, also `.agents/skills/<name>/SKILL.md`)

| Skill | When |
| --- | --- |
| `plan` | start any non-trivial task: inspect, propose, wait for a yes |
| `spec` | a feature or component changes: write its spec sections first |
| `build` | implement in thin, tested slices |
| `test` | add or fix tests; never weaken an assertion |
| `graph` | before changing or tracing code: `knowledge/`, then `tools/architecture/ask`, then the language server |
| `debug` | reproduce a failure, test one hypothesis, fix its root cause and prove the regression |
| `sign-off` | end a task: tests, docs and links, task state, security on sensitive diffs |
| `pr-review` | a pull request is open: fresh reviewer, verified findings, one verdict comment |
| `ship` | merge and release behind green CI and the deploy's own guards |
| `okf-update` | a documented fact changed: the right doc, its links, its caps |
| `security-scan` | a sensitive diff, or the weekly job: `/claude-security` |
| `tech-watch` | the monthly technical watch |
| `pr-taste` | the owner commented on a PR: keep the durable preferences |
| `retro` | weekly or after a long session: lessons into hooks, skills and preferences |
| `loop-triage` | an outer-loop finding: turn it into an issue an inner loop can fix |
| `adopt` | apply Æion Factory to an existing repository, one phase at a time |

## Tools

```bash
tools/knowledge/knowledge-check                       # the knowledge base: frontmatter, links, caps
tools/knowledge/doc-links                             # docs <-> code links resolve (L1, L2)
tools/knowledge/doc-links --diff origin/develop --worktree   # also: code changed without its doc (L3)
tools/knowledge/doc-links --for path/to/file          # the docs covering a file
tools/repo-health/repo-health [--json]                # comment blocks, file sizes, AGENTS.md and work caps
tools/factory-tests                                   # every factory test suite below, as CI runs them
python3 tools/knowledge/tests/test_doc_links.py       # tool tests
python3 .claude/hooks/test_hooks.py                   # hook tests
python3 tools/context/tests/test_budget.py            # context boundary/recovery regressions
python3 tools/architecture/tests/test_refresh.py       # analysis provenance/coverage regressions
tools/architecture/setup                             # installs pinned Graphify/Archify locally: ask the user first
tools/architecture/refresh --target HEAD              # recommended initial baseline, after commit
tools/architecture/refresh --target HEAD --base origin/main  # refresh the reviewed change
tools/architecture/refresh --check --target HEAD       # verify persisted artifact hashes
tools/agents/run claude                             # existing Claude integration
tools/agents/run codex                              # trust project/hooks first
tools/agents/run opencode                           # native plugin, shared skills
tools/agents/run opencode --isolated                # separate local profile on database conflict
tools/agents/run pi                                 # Pi 1.4.0+, project extension in .pi/
tools/agents/run devin                              # Devin CLI: .devin/ hooks, shared skills
tools/agents/run droid                              # Droid (factory.ai): .factory/ hooks, shared skills
tools/agents/run antigravity                        # Google Antigravity CLI (agy): .agents/hooks.json
git config aeion.mode local                         # local play: commits on main, no PR/CI ritual
git config --unset aeion.mode                       # back to the team flow
python3 tools/agents/tests/test_hooks.py
node --test tools/agents/tests/test_opencode.mjs
node --test tools/agents/tests/test_pi.mjs
python3 tools/loops/tests/test_loops.py
tools/context/run                                    # original Claude entry, native compact at 250k
tools/context/run --strict                           # refuses: strict pre-request control unqualified
python3 tools/context/budget.py checkpoint --session <id> --base origin/develop --verify
```

## Loops (`loops/orchestrator.sh`)

```bash
loops/orchestrator.sh <job> [--dry-run] [--issue N]
#   inner     fix one `agent-ready` issue: branch, PR, review (needs --issue)
#   middle    review the open pull requests that have no verdict yet
#   outer     daily read-only health: production (read-only path), CI, open findings -> issues
#   docs      daily docs drift: doc-links, knowledge-check, stale dates -> issues
#   security  weekly codebase scan with /claude-security -> issues
#   watch     monthly technical watch -> a tech-watch package via PR
#   retro     weekly lessons from sessions and PR comments -> a PR of hooks, skills, preferences
```

Every job reads `env.local`, refuses to run while `LOOPS_ENABLED=false` (except `--dry-run`), and
prints the managed command it runs. Schedules: `loops/schedules/`.
The command uses `tools/context/run` so enabled loops get the same architecture-baseline check
(a warning when absent) and the same native compaction profile. Dry-run still launches no agent or integration.

## Plugins (Claude Code)

```bash
claude plugin marketplace add anthropics/claude-plugins-official
claude plugin install hookify@claude-plugins-official --scope project
claude plugin install skill-creator@claude-plugins-official --scope project
claude plugin install claude-security@claude-plugins-official --scope project
claude plugin marketplace add ./tools/claude-plugins --scope project      # local language servers
claude plugin install native-lsp@aeion-local --scope project
```

## Git flow

The team flow below is the default. In local mode (`git config aeion.mode local`), commit on
`main` locally; pushing is the user's call (pre-push lets it through), and the safety guards still
apply. Only the user switches the mode.

```bash
git switch develop
git commit -m "<what changed and why>"            # the pre-commit runs the checks
# in a session: run the skill pr-review, which records Review: OK for HEAD
git push origin develop                           # pre-push refuses it without that verdict
# release: skill ship (pull request develop -> main, merge commit, tag vX.Y.Z)
```
