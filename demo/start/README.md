# Starting a harness

These notes are for the person who launches a run. Each folder holds the first message to paste into
one harness:

| Harness | First message | Open it in |
| --- | --- | --- |
| Claude Code | [`claude_code/START.md`](claude_code/START.md) | the repository root |
| Codex | [`codex/START.md`](codex/START.md) | the repository root |
| OpenCode | [`opencode/START.md`](opencode/START.md) | the repository root |
| Pi | [`pi/START.md`](pi/START.md) | the repository root |
| dsh | [`dsh/START.md`](dsh/START.md) | the repository root, with the patch flag below |
| Orchestrator (run 2) | [`orchestrator/START.md`](orchestrator/START.md) | the repository root, in Claude Code |

Every message assumes the agent was opened at the repository root, and every path in it starts there.
Run 1 opened them in `demo/`: a sandbox that writes only below its folder then refused `.git` and the
root `work/` the 250k closure writes (dsh FB-003, FB-006). Run one harness at a time: two sessions in one
tree make each other's checkpoint receipts stale (OpenCode FB-003).

## Launch commands, without a terminal UI
A driver (a person, or the [orchestrator](orchestrator/START.md)) sends `START.md` as the first prompt,
then one message per round in the same session. Each CLI's `--help` was read on 2026-10-29; the models
and effort are run 2's choice.

| Harness | First prompt | Next prompts, same session |
| --- | --- | --- |
| Claude Code | `claude -p --model <opus> --effort high --session-id <uuid> "<prompt>"` | `claude -p --resume <uuid> "<prompt>"` |
| Codex | `codex exec -m <model> -c model_reasoning_effort="high" --json "<prompt>"` | `codex exec resume <session-id> "<prompt>"` |
| OpenCode | `opencode2 run -m <provider>/<model>#high -s <id> --format json "<prompt>"` | the same command, same `-s <id>` |
| Pi | `pi -p --model <provider/id>:high --session-id <id> "<prompt>"` | the same command, same `--session-id` |
| dsh | `dsh headless --patch .dsh/factory.patch.yml --json "<prompt>"` | add `--session-id <id>` from the first run's events |

A headless run has no approval channel: what the sandbox refuses stays refused, and the agent reports
it. Keep each run's event stream as `demo/<harness>/journal/run.jsonl` (git-ignored).

## Before any run
- **Tools on this machine:** Deno 2 (the app, and `ask callers`), Docker (round 06), Node and Chrome
  (Archify, round 07). Each run installs nothing on its own.
- **The graph:** `tools/architecture/ask` needs the Graphify venv in `.aeion/integrations/graphify-venv/`.
  It is already set up here. On another machine, run `tools/architecture/setup` once from the root.
- **A clean folder:** `demo/<harness>/` has an empty `journal/`, `feedback_for_aeion/` and `render/`,
  plus a `workspace/` copied from the factory with no app in it. To start again, archive the last run
  as [`../claude_code/archive/run-01/`](../claude_code/archive/run-01/SUMMARY.md) shows, keep the old copy
  as `workspace.run-NN/` (git-ignored, never deleted), then copy the factory again:
  ```bash
  mv demo/<harness>/workspace demo/<harness>/workspace.run-01 && mkdir -p demo/<harness>/workspace && git archive HEAD -- . ':(exclude)demo' | tar -x -C demo/<harness>/workspace && tools/release/release --new-project --root demo/<harness>/workspace
  ```

## Skills and hooks, per harness
The skills live once, in `.claude/skills/`. `.agents/skills/` holds links to them for the other harnesses.
The hooks all call the same guards in `.claude/hooks/` and `tools/agents/hook.py`. The full runbook is
[`knowledge/operations/agent-compatibility.md`](../../knowledge/operations/agent-compatibility.md).

### Claude Code
- **Nothing to set up.** The root `CLAUDE.md`, the `.claude/settings.json` hooks and `.claude/skills/`
  are found from the root.
- **Duplicate skills.** The skill list also shows the copy's own skills, as `demo/<harness>/workspace:<skill>`.
  Those belong to the copy; the agent uses the root ones.
- **Hooks check.** Type `/hooks` and look for the `PreToolUse` guards.

### Codex
- **Trust and hooks.** Trust the project in Codex's settings, enable the project hooks in `/hooks`, then
  restart Codex. A trusted project and trusted hooks are two separate approvals; both are needed.
- **Skills.** Codex finds them through the `.agents/skills/` links. Nothing to copy.
- **Commits.** Opened at the root, the workspace-write sandbox covers `.git`; if your settings keep it
  narrower, the agent stops with the commit command and you run it.

### OpenCode (2.1.4+)
- **Command.** On this machine OpenCode 2.2.0 is `opencode2` (`~/.opencode/bin/`, on the interactive
  shell's PATH only): call it by that path from a non-interactive shell.
- **Skills and hooks load on their own.** The root `opencode.json` points at the skills, and the plugin
  in `.opencode/plugins/factory/` runs the hooks.
- **Provider.** Connect it in OpenCode's own settings.
- **Database conflict.** If OpenCode refuses to start, use `tools/agents/run opencode --isolated` from the
  root. It uses a separate local profile, so you connect the provider once more there.

### Pi (1.4.0+)
- **Start command.** Pi loads project extensions from the folder it starts in, so from the root it finds
  `.pi/extensions/factory/` with no flag. Started anywhere else, pass it: `pi -e <root>/.pi/extensions/factory/index.js`.
- **Trust prompt.** Accept it, so Pi reads the project.
- **Skills.** Found through `.agents/skills/` in a parent folder.
- **Provider.** Connect it in Pi's own settings.
- **Sign-off at stop.** The extension forwards `agent_before_settle` as Stop, so `sign-off.py` runs
  (Pi FB-004, fixed after run 1); any silence from it is a feedback.

### dsh (DeepSeek Harness, 0.2.1+)
- **Start command.** dsh loads project plugins only through a patch overlay, so pass it from the root:
  ```bash
  dsh web --patch .dsh/factory.patch.yml
  ```
  dsh ships no `tui` profile; `web` is its interactive chat, in the browser. A background `dsh web` on
  port 3080 makes this fail with `EADDRINUSE`: add `--port 0`. Headless needs no browser
  or port: `dsh headless --patch .dsh/factory.patch.yml --json "<task>"`, then `--session-id <id>`. See
  [`../dsh/README.md`](../dsh/README.md).
- **Skills.** Found through `.agents/skills/` in a parent folder.
- **Provider.** Connect it in dsh's own settings.
- **Compaction.** Once the handover is verified past 250k, the agent asks you to type `/compact` (web keeps
  compaction out of a plugin's reach; headless compacts by itself), then reads its handover.
- **Sign-off at stop.** The plugin forwards `agent/turn-stopping` as Stop and steers the start context
  into the first step (dsh FB-001, FB-002, fixed after run 1).

## Check the hooks before round 00
In any harness, ask the agent to run `cat .env`. The guard must refuse it and name its rule. If the
command runs, or the agent reports no refusal, the hooks are not loaded. Fix the setup above before
going on.
