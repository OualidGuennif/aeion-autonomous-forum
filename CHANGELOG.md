# Changelog

Every notable change, newest first ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
[SemVer](https://semver.org/)). Each change adds its line under Unreleased; `tools/release/release`
dates them at release time. Spec: knowledge/policies/engineering.md

## [Unreleased]

## [1.8.6] - 2026-11-01

- dsh web no longer stalls the 250k/300k gate as advisory: the plugin reaches the per-agent compaction
  service through `agent.ctx.get('compaction')` (the web profile keeps it inside each agent preset), so a
  verified handover compacts and resumes instead of waiting for dsh's own pressure compaction.

## [1.8.5] - 2026-11-01

- dsh no longer corrupts its own sessions: the plugin's `say` helper steers every message with
  `role: "user"` as the harness validator requires, so a steered start context or warning no longer
  produces a user/message the session refuses to reopen on the next launch.
- `tools/agents/adapter-check` reports `UNROLED` for a `steer`, `inject` or `followup` call whose
  message carries no role, beside `UNIDENTIFIED` for a missing id, so a connector that would persist
  an ill-formed message fails the check instead of a session.

## [1.8.4] - 2026-11-01

- The 250k/300k rule no longer stalls a harness that cannot compact: the dsh web plugin tells the
  shared rule that its profile offers no compaction service, so the gate is advisory there and the
  harness compacts on its own instead of waiting for a human to type `/compact`; the handover stays
  required for recovery.
- Pi no longer cancels its own compaction at the model window when the checkpoint is unverified: the
  compaction runs, resumes editable, and owes the receipt after it, as Codex and Droid already did.
- `tools/agents/dsh-install` installs the desktop dsh plugin from a release tag and links the profile
  at that copy instead of the working tree, so a broken `develop` cannot reach the app; `--check`
  names a hand-edited copy, a missing file and a link that names no tag.
- `tools/agents/adapter-check` reports `UNIDENTIFIED` for a `steer` call that carries no id: dsh
  persists such a message and then refuses to reopen the session that holds it.
- `tools/agents/adapter-check --ref <tag|main|develop>` checks every connector against a git
  reference instead of the working tree, so a project on a branch verifies the same way a pinned one
  does; a file the reference predates is skipped rather than failed.
- `tools/agents/template-update` moves an adopted project to a release: it writes the connector files
  a harness loads, writes `VERSION`, and only names the files the project owns (`AGENTS.md`, merged
  settings, `knowledge/`, `work/`, `CHANGELOG.md`) for a human to merge.
- Pi headless: `pi -p` never trusts the project, so discovery loads no extension and the session runs
  with no Æion Factory hook; pass `-e .pi/extensions/factory/index.js` explicitly. A headless JSON run
  with `-e` injected the checkpoint profile and the bash guard refused an environment dump; without it
  the same session was unguarded.
- Skill `template-update` states the three file classes and the rule behind them: the project's own
  text wins, the harness connector files are the factory's, byte for byte.

## [1.8.3] - 2026-11-01

- `tools/agents/adapter-check` names a connector copy that drifted from the factory and a native config
  that points at a missing script, and refuses a project's own copy of itself; `tools/factory-tests`
  runs it with `--self`.
- `.claude/hooks/prompt-defense.py` flags injected instructions, hidden characters and encoded payloads
  in a fetched, MCP or `curl`'d tool result, once per session; it never blocks.
- dsh, Pi, OpenCode and Codex deliver that warning (dsh also needed a message id: its second steered
  message in a turn was dropped as a pending duplicate).
- `knowledge/policies/security.md` states the prompt-defense baseline; `knowledge/policies/engineering.md`
  the three passes: make it work, make it right, follow practice.

## [1.8.2] - 2026-10-31

- dsh and Pi: a terminal stop (`continue: false`) ends the turn with its reason instead of being steered
  as another request; the dsh adapter re-steered every stop answer, and a live headless run against a hook
  that always stops sent 51 Stops in 90 seconds without exiting, where the fixed adapter sends one. A
  sign-off finding still steers once, and Pi keeps its bounded three-request closure alert.
- At 300k a failing closure check no longer blocks the compaction that ends the closure: the hook signals
  that compaction whether or not the checkpoint verifies, code edits stay open in the new epoch, and the
  closure stays owed — Stop is refused until a verified checkpoint, and Codex and Droid keep their own
  early-compaction path — so a check that failed before the session can be repaired instead of deadlocking.
  The web profile asks the user to compact in the hook's own words, so it no longer claims a verified
  handover it does not have.

## [1.8.1] - 2026-10-31

- Pi: the 250k alert and the settlement refusal name the checkpoint command, so an agent is no longer
  asked to verify a receipt it cannot discover; a settlement that still owes closure is continued three
  times, not forever; closure admits a project's own Markdown docs, never code.
- The checkpoint reports a failing check's bounded output instead of a bare exit status, and its
  suggested base is an existing ref (`origin/develop`, `origin/main`, else `HEAD`) — a local clone
  with no remote no longer gets an impossible `origin/main` command.

## [1.8.0] - 2026-10-31

- Validate evaluation report input and preserve unknown optional metrics as `N/A` / JSON `null`
  instead of zero; document separate datasets for baseline comparisons.
- Refuse missing knowledge roots, a missing orphan-check entry index and explicitly empty review dates.
- Preserve JSONL record separation when appending after a missing final newline; correct the
  handover evaluation check to expect ignored recovery state.

- Add `tools/knowledge/stale` and `tools/knowledge/orphans`, with fixture tests, and date the
  volatile operations and references documents with `review_after`.
- Make `AGENTS.md` a procedure: a five-question self-check, stop-and-ask triggers, recently changed
  pointers, and four inline rules with the rest owned by their docs.
- Add a local evaluation record and report for the golden task set, with tests and a runbook; no model calls.
- Refuse `nan` and `inf` for the evaluation record's float flags, so one run cannot write a JSONL line no parser reads.
- Ship twelve evaluation tasks, each with a fixed prompt, the gate that decides it and an empty baseline.
- Add `tools/skills/lint` and its fixture tests, so every native skill's name, trigger, description
  and body caps are checked.

## [1.7.0] - 2026-10-31

- Strengthen the 16 native skills with evidence-led review, diagnostic/test boundaries and honest
  completion checks; add one native-first selector for the optional passive project-local pool. Source
  agent profiles do not authorize delegation, model changes, installs or generic numeric gates.
- Ship a self-contained pool of 525 skill folders and 99 agent profiles with resources, licenses,
  source pins and offline integrity/search tooling; the lifecycle folder is not a dependency.
- Keep SAST enabled for the pool, with three reviewed per-rule annotation comments bound to
  original source hashes; source changes require renewed review rather than silent suppression.

## [1.6.2] - 2026-10-29

- Keep reasoning private and omit optional progress narration, including JSON notes; token feedback
  now counts JSON progress notes and recommends direct tool use without blocking work.

## [1.6.1] - 2026-10-29

- Simplify the README around quick start, all 16 skills and optional tools; keep detailed setup,
  adoption and dsh troubleshooting in a linked getting-started guide.
- demo: the run 2 launch commands use OpenCode 2.2.0's `opencode2 run`, read from its `--help`.

## [1.6.0] - 2026-10-29

- Droid registers PreCompact: a compaction before a verified handover runs and closure (handover,
  `work/current.md`, docs, checkpoint) is owed right after it, as for Codex. Droid still has no 250k alert.
- dsh starts with its `web` profile (it ships no `tui`; the demo's `--profile tui` command failed), and the
  guards now run there: compaction is a separate plugin entry, since web keeps that service inside each
  agent preset. Without it the agent asks the user for `/compact`, and a token fall marks the compaction.
  The plugin now guards each session by its own working directory, so one background `dsh web` server
  (started anywhere with the patch's absolute path) guards the sessions opened in a factory project.
- The demo gains a dsh run (`demo/dsh/`, `demo/start/dsh/START.md`); every demo workspace has the 1.5.0 hooks,
  skills and tools (Codex and OpenCode keep their app and run state).
- demo: run 1 of Codex, OpenCode, Pi and dsh archived with a comparison table; the run 2 brief opens every
  harness at the repository root, one at a time, and an orchestrator brief drives them through their CLIs.
- `tools/architecture/gallery <render>` builds one self-contained `index.html` from Archify diagrams: each
  render is inlined as a lazy iframe `srcdoc`, so the gallery opens from `file://` with no server; a diagram
  JSON without its rendered HTML is refused. It replaces the Pi run's own builder (Pi FB-003).
- Fixes from the demo run 1 feedback. The Bash guard no longer refuses an environment copy passed to a child
  process (`env=dict(os.environ, K=V)`), prose naming a secret file that `cat`/`tee` write to a file from a quoted
  heredoc, or a `cat` of an absolute path after a `cd` (it is sized). `ask` reads each spec's `sources` from
  disk, so a frontmatter-only edit is no longer hidden by Graphify's cache. `doc-links` reads `- resource:`
  list entries and fails (L4) when a file's `Spec:` doc omits it from its sources. The 250k/300k messages name
  the handover headings, a stale checkpoint receipt names the paths that changed, every harness's live
  journal log is git-ignored, and `knowledge-check` prints the task entry shape. dsh steers the start context
  and forwards a turn's close to the sign-off (`agent/turn-stopping`); Pi does it at `agent_before_settle`.

## [1.5.0] - 2026-10-29

### Fixed
- Live runs fixed two closure stalls: OpenCode's file tools (`path`) can now write the handover in
  closure and its verified compaction ends the closure.
- Codex no longer stalls around compaction: it compacts natively at 250000, a compaction that comes
  before the verified handover runs and the session resumes in closure until the checkpoint passes
  (owner decision), and closure lets shell-only harnesses read files with plain read commands.
- Pi resumes by itself after the compaction its extension starts (the compaction aborts the turn).
- Devin and Droid hooks pass their event name on the command line and fall back on a per-clone key, so a
  payload without an event name or session id is still guarded instead of stopping the session.

### Added
- DeepSeek Harness (dsh) support: `.dsh/plugins/factory/` applies the shared guards and the 250k/300k
  rule from dsh's token meter and compacts before the next step; `tools/agents/run dsh` loads it.
- `tools/release/release --new-project` starts a copy of the factory as a new project: `VERSION` 0.1.0, an
  empty changelog and only the "Adapt" task in `work/current.md`. It refuses in the factory's own clone.
  The README's New project steps and the demo copy command run it (FB-003 of Claude Code run 1, FB-001 of
  the Codex run).

### Changed
- One context rule for Claude, Codex, OpenCode, Pi and dsh: 250k closure and verified handover before compaction, and 300k forces the handover and compaction again instead of stopping. Codex reads its rollout tokens, OpenCode its step tokens, Pi its context usage; OpenCode and Pi compact themselves.

## [1.4.0] - 2026-10-28

### Added
- `tools/architecture/ask callers <file>:<name or line>`: who calls a symbol, resolved by type through the
  project's own language server (`deno lsp`, or a TypeScript 7 `tsc --lsp`), in any harness. Installs
  nothing; without a server it exits 1 with the search to run instead.
- The Pi harness (1.4.0+): `.pi/extensions/factory/` sends Pi's tool, start and compaction events to the
  shared guards, `tools/agents/run pi` checks the version, and `demo/pi/` opens it in the playground.
- `tools/architecture/ask` and the skill `graph`: exact, deterministic answers from a Graphify graph of the
  working tree, rebuilt only when a file changed (`find`, `deps`, `rdeps`, `path`, `tests`, `untested`,
  `spec`, `files`, `hubs`, `status`). Names match exactly (exit 3 ambiguous, 4 none), and a call Graphify
  inferred by name alone counts only when the files import each other. No server and no LLM.
- `graph.py` adds `specified_by` edges from `Spec:` lines, `names_file` edges from tests that name a file
  they run, and exact `imports` from `deno info` in a Deno project; `coverage.json` counts each.
- The Bash guard refuses Graphify's LLM and installer commands (`extract`, `label`, `provider`, `add`,
  `clone`, `global`, `install`, `cluster-only` without `--no-label`).
- `docs/diagrams.html` links every Archify diagram; `tools/architecture/refresh` rebuilds
  `.aeion/analysis/index.html`, a listing of the PASS baselines, newest commit first.
- `demo/`, a playground where an agent builds a real app on a copy of the factory: briefs in
  `demo/plan/`; each `demo/<harness>/` keeps its journal and `feedback_for_aeion/`, and only its `workspace/` is local.

- `tools/factory-tests` runs every factory test suite, as CI now does; `AGENTS.md` names it in one line
  instead of five, so a project has room under the 120-line cap for its own build commands.

### Changed
- Graphify is pinned to the 0.9.97 release and Archify to the v3.2.0 release tag (was 0.9.95 and 13
  commits past v3.2.0); only optional extras changed upstream, so the hashed requirements are unchanged.
- Release source archives leave out `demo/` (`.gitattributes` export-ignore); a release test checks it.

### Fixed
- OpenCode started in a subfolder (`demo/`) finds the project's hooks in the nearest folder above that has
  them, instead of refusing every tool call.
- `doc-links` skips a project nested in this one (its own `AGENTS.md` and `knowledge/`), whose `Spec:`
  pointers name its own knowledge; a demo copy no longer fails the parent's L1 and L3.
- The Bash guard's bounded-intake refusal names its cause (oversized file, variable or glob path, a
  `cat` after a `cd`) instead of always asking for a limiter.
- The Bash guard no longer reads a code property such as Deno KV's `entry.key)` as a key file, in a
  quoted heredoc read by an interpreter or git or written by `cat`/`tee`; unquoted, shell, `xargs`
  and read-loop heredocs stay strict.
- The Graphify graph links each spec to the files its frontmatter `sources` name (`specifies` edges),
  so "which spec covers this file" is answered; unresolved resources are listed in `coverage.json`.
  `graph.py` also accepts a relative root.
- `tools/architecture/refresh` refuses a root that is not its own git repository before any git call,
  instead of analysing the enclosing repository; the doc gives the plain-folder commands.
- Without git, the file walk honours each folder's simple `.gitignore` rules, so build output such as
  Fresh's `app/_fresh/` no longer fails `repo-health` in a plain folder.
- At the 250k alert the checkpoint is admitted from any working directory (relative, absolute or
  `cd <root> &&`), and without `--base` in a plain folder, which could not close before; a refused
  checkpoint names the missing heading or the remaining TODO.

## [1.3.0] - 2026-10-28

### Added
- A `develop` branch for daily work: `tools/git-hooks/pre-push` refuses a push of `develop` without a
  recorded `Review: OK` for the exact commit, and any direct push to `main`.
- Versioning: `VERSION`, this changelog and `tools/release/release`; releases are tagged `vX.Y.Z`.
- Token feedback (advisory): one line, once per turn, when an agent writes prose between tool calls
  or rewrites an existing file of 200+ lines with Write; Claude Code and Codex are covered.
- Plain folders without git: hooks, link and health checks, the context checkpoint and the
  session-start summary work on the files on disk.

### Changed
- Local mode (`git config aeion.mode local`) pushes `main` without the pre-push team gate.
- The context guard finds a workflow agent's transcript under `subagents/workflows/<run>/`, so
  workflow agents (such as a Claude Security scan) are no longer refused every tool call.
- Releases reach `main` through a reviewed `develop → main` pull request merged with a merge commit.
- Main protection allows merge commits and no longer requires linear history, so `develop` stays an
  ancestor of `main`.
- Sign-off and the checkpoint compare against `origin/develop` when it exists.
- CI also runs on pull requests into `develop` (loop pull requests); pushes of `develop` run none.
