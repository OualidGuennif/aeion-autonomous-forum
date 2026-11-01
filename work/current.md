# Current work

Task state only. One entry per open task, newest first. At most 150 lines, 10 per entry.
Finished tasks are deleted: the commit message and knowledge/ keep what matters.
Status: ACTIVE, WAITING-OWNER, MEASURING, BLOCKED, BACKLOG. An entry is `## <title> — <STATUS>`, then the
lines `- Owner decision:`, `- State:`, `- Next:` (with who), `- Blocker:`, `- Evidence:`.

## Connector drift check across all harnesses — ACTIVE
- Owner decision: 2026-11-01, "avoid such problems for all connectors": a stale or hand-edited copy must fail a check, not a session. Same day: the prompt-defense baseline and the three passes ship in 1.8.3 too. Later the same day: verification must accept `main`/`develop` for people who downloaded a branch, and an adopted project needs an update path that keeps its own content.
- State: `tools/agents/adapter-check` compares the 27 connector files with the factory copy or with any git reference (`--ref v1.8.5`, `--ref main`, `--ref develop`), checks each config's script references, refuses a project's own copy of itself, and reports `UNIDENTIFIED` for a `steer` call with no id and `UNROLED` for a `steer`/`inject`/`followup` with no role. The dsh plugin's `say` helper steers every message with `role: "user"`, so a steered start context or warning no longer corrupts a session the harness refuses to reopen. `tools/agents/template-update` moves an adopted project to a reference: it writes the connector files and `VERSION` and only names `AGENTS.md`, merged settings, `knowledge/`, `work/` and `CHANGELOG.md` for a human to merge; skill `template-update` states the three file classes. `tools/agents/dsh-install` pins the desktop profile at a release tag; `--check` fails a hand-edited copy and today's working-tree link. `connectors.py` and `refs.py` hold the one file list and the one reference reader. The 250k/300k gate no longer waits for a human: the dsh web plugin reports the compaction service it cannot reach, so the rule is advisory there, and Pi lets its own window compaction run and owes the receipt after it.
- Next: v1.8.5 released (PR #31, tag `v1.8.5`) and the desktop plugin installed at that tag; restart the desktop app so the running server loads it, then run `tools/agents/adapter-check --root <project> --ref v1.8.5` in every adopted project and in its CI (who: owner restarts, agent checks adopted projects).
- Blocker: none.
- Evidence: CHANGELOG.md Unreleased; knowledge/operations/agent-compatibility.md; knowledge/operations/context-safety.md; knowledge/operations/build-and-test.md; tools/agents/tests/test_dsh_install.py; tools/agents/tests/test_template_update.py; tools/agents/tests/test_adapter_check.py; tools/agents/tests/test_hooks.py; tools/agents/tests/test_pi.mjs; tools/agents/tests/test_dsh.mjs; live dsh headless run in an isolated copy attempted an environment dump and quoted the guard's refusal verbatim; the same payload is denied through all seven adapters.

## Golden task set — MEASURING
- Owner decision: 2026-10-31, start the local evaluation harness, then approve the twelve candidate tasks proposed with it.
- State: twelve tasks in `tools/evals/tasks/`, six implementation and six process; one baseline is recorded (architecture-answer on pi, success, 0.9 minutes) and eleven still say "not run yet"; `report` renders one row.
- Next: one baseline per harness on the same fixed commit, then compare (who: owner; an agent prepares each run).
- Blocker: one harness measured, so no across-harness comparison is possible yet.
- Evidence: knowledge/operations/evals.md; tools/evals/tasks/architecture-answer/BASELINE.md

## demo/ Idea Board run 2 — WAITING-OWNER
- Owner decision: 2026-10-28, each harness builds the Idea Board in a git-less copy, journals each round and files feedback; 2026-10-29, the run 1 wrap-up plan and run 2 driven by one orchestrator session.
- State: run 1 done and archived for all five harnesses (demo/<h>/archive/run-01/, comparison in demo/README.md); its feedback fixed in the factory (c3f586f, fd288e9) or in the run 2 brief (e1f30ad); orchestrator brief in cfc92e1.
- Next: run 2 is planned for the week of 2026-11-02, not before; on the owner's go, a Claude Code session runs demo/start/orchestrator/START.md, one harness at a time (who: owner, then agent).
- Checked 2026-10-29: the four run 1 galleries load 14/14 previews from file:// in headless Chrome, viewer opens, pages and closes; OpenCode is `opencode2` 2.2.0.
- Blocker: none.
- Evidence: demo/README.md; demo/start/orchestrator/START.md; work/handovers/b5bf5c06-1101-47fa-a0b7-f0ca1ac15381.md

## Live Devin, Droid, Antigravity and Pi hook qualification — WAITING-OWNER
- Owner decision: 2026-10-27, support Devin, Droid and Antigravity; 2026-10-28, Pi 1.4.0; adapters use documented, not live-run, payloads.
- State: Devin merged in #12, Droid in #13, Antigravity in task/antigravity, Pi on develop. Pi ran live headless on 2026-11-01: with `-e .pi/extensions/factory/index.js` the extension injected the checkpoint profile, an allowed read succeeded and the shared guard refused an environment dump; Pi's own native compaction is still unexercised.
- Next: run the qualification steps with Devin CLI, Droid, agy and Pi and record the result (who: owner).
- Blocker: neither CLI is in the agent's container.
- Evidence: knowledge/operations/agent-compatibility.md

## Native strict context admission — BLOCKED
- Owner decision: 2026-10-26, attempt native 300k qualification using paid OpenCode Go DeepSeek V4.1 Flash.
- State: primary HTTP denial, 278k-input accounting and over-budget tool continuation exercised in isolation; strict launch still refuses.
- Next: qualify a portable counter, auxiliary calls and the full durable handover/compaction cycle before enabling strict admission.
- Blocker: the experimental counter/profile is not shipped; provider framing and complete native lifecycle coverage remain unqualified.
- Evidence: knowledge/references/opencode-context-qualification.md; knowledge/operations/context-safety.md

## Adapt Æion Factory to this project — ACTIVE
- Owner decision: <date>, started from Æion Factory (knowledge/decisions/ADR-001).
- State: the structure is in place; AGENTS.md still describes Æion Factory, not the project.
- Next: write the project's paragraph and build commands in AGENTS.md; walk efficiency/ phases 1 to 7 (who: agent, with the owner's yes per phase).
- Blocker: none.
- Evidence: efficiency/00-EFFICIENCY.md
