# Æion Factory

**Your coding agent. A clear workflow. Checks you can verify.**

A repository template that gives coding agents shared instructions, reusable skills, linked
documentation and automatic safety checks. Your project's rules and your decisions come first.

[New project](#start-a-new-project) · [Existing project](#existing-project) · [Optional tools](#enable-optional-tools) · [Skills](#skills) · [Hooks](#hooks) · [Docs](#documentation)

## Start a new project

You need **Git, Python 3.12+ and one installed, connected coding agent** (macOS, Linux or WSL).

1. On GitHub, [create a repository from this template](https://github.com/OualidGuennif/aeion-factory/generate).
2. Clone it, reset it as a new project, and start your agent:

```bash
git clone https://github.com/<you>/<your-project>.git
cd <your-project>
tools/release/release --new-project
tools/agents/run claude
```

`--new-project` sets version 0.1.0, empties the changelog, leaves one task (adapt the factory to
your project), removes the factory's `demo/` and records the factory release in `.aeion-factory`
for later updates. Then give your agent the
[ready-to-copy setup prompt](knowledge/operations/getting-started.md#new-project).

Replace `claude` with `codex`, `opencode`, `pi`, `dsh`, `devin`, `droid` or `antigravity`. The
launcher installs nothing: trust the project and approve its hooks as your agent asks
(**Codex: enable the hooks in `/hooks`, then restart**). To check the hooks are loaded, ask the agent
to run `cat .env`: it must be refused. [First-run setup per agent →](knowledge/operations/getting-started.md#prepare-your-agent)

**You start in local mode.** The safety hooks are on; the git lifecycle (a `develop` branch,
a review before each push, a release pull request) is off until you
[enable the team git flow](#team-git-flow).

To try the factory itself, clone `https://github.com/OualidGuennif/aeion-factory.git` and run
`tools/agents/run claude` in it.

## Existing project

Open your project with your agent and use the **adopt** skill from a reference copy of Æion Factory.
It inspects what you have and proposes one phase at a time; your instructions, tools and history
stay in place, and you approve each change before it is applied.
[Existing-project walkthrough and ready-to-copy prompt →](knowledge/operations/getting-started.md#existing-project)

### Update to a new release

From an up-to-date clone of Æion Factory (`git fetch --tags`), not the project's own copy of the
tool, preview, then apply the latest release tag:

```bash
tools/agents/template-update --ref v2.0.2 --root ../your-project --check
tools/agents/template-update --ref v2.0.2 --root ../your-project
```

It finds the release your project came from (`.aeion-factory`, else the closest tag), shows the
release notes, updates every factory file you left untouched, merges the ones you edited, and leaves
your instructions, settings, knowledge and changelog to you with a proposal for each. The
[template-update skill](.agents/skills/template-update/SKILL.md) walks your agent through it.

## Enable optional tools

Everything here is off until you turn it on. Run these commands yourself: the agent's guard refuses
to switch the flow mode for you.

| Option | What you get | Turn on |
| --- | --- | --- |
| [Team git flow](#team-git-flow) | Work on `develop`, checks before each commit, a recorded review before each push, releases by pull request to `main` | 4 commands below |
| [Graphify + Archify](#graphify-and-archify) | Dependency queries and architecture diagrams built from source, no LLM | `python3 tools/architecture/setup` |
| Claude Code plugins | Optional hook, skill, security and language-server tools | [Plugin commands](command.md#plugins-claude-code) |
| Scheduled loops | Recurring checks, reviews and approved tasks | [Loop setup](knowledge/operations/loops.md) |

### Team git flow

```bash
git config aeion.mode team                  # turn the team flow on for this clone
git config core.hooksPath tools/git-hooks   # pre-commit checks and the pre-push review gate
git switch -c develop                       # or: git switch develop, when it already exists
git push -u origin develop
```

From then on the agent works and commits on `develop`, runs the skill **pr-review** before each
push (the pre-push hook refuses `develop` without a `Review: OK`), and releases with the skill
**ship**: a pull request `develop → main`, merged after green CI, then tagged. On GitHub, protect
`main` so it only takes pull requests with passing checks.
[The full flow, step by step →](Workflow_Agent.md)

Back to local mode: `git config --unset aeion.mode`. The pre-commit checks also work on their own in
local mode: run only the `core.hooksPath` line.

### Graphify and Archify

`python3 tools/architecture/setup` creates a virtual environment with pinned packages and an Archify
clone in `.aeion/`. It needs **Node 18+ and Chrome/Chromium** (Python 3.14.1 is excluded). The agent
explains the installation and asks before running it; on a work computer, check your company's
policy. [Installation and first analysis →](knowledge/operations/architecture-maintenance.md)

## Skills

A skill is a reusable procedure your agent follows for one kind of task. Ask naturally
(**"Use the plan skill to prepare this change."**); Claude Code also accepts `/plan`, Codex `$plan`.

| You want to… | Skills |
| --- | --- |
| Pick task guidance, without launching agents | [skill-route](.claude/skills/skill-route/SKILL.md) — native skills first, optional passive pool |
| Plan a change and define its behaviour | [plan](.claude/skills/plan/SKILL.md) · [spec](.claude/skills/spec/SKILL.md) |
| Implement, test or fix a bug | [build](.claude/skills/build/SKILL.md) · [test](.claude/skills/test/SKILL.md) · [debug](.claude/skills/debug/SKILL.md) |
| Find dependencies, affected tests and specs | [graph](.claude/skills/graph/SKILL.md) — needs Graphify + Archify |
| Update the docs and finish a task | [okf-update](.claude/skills/okf-update/SKILL.md) · [sign-off](.claude/skills/sign-off/SKILL.md) |
| Review and release (team flow) | [pr-review](.claude/skills/pr-review/SKILL.md) · [ship](.claude/skills/ship/SKILL.md) |
| Check a sensitive change | [security-scan](.claude/skills/security-scan/SKILL.md) |
| Adopt the factory, or update to a new release | [adopt](.claude/skills/adopt/SKILL.md) · [template-update](.agents/skills/template-update/SKILL.md) |
| Triage findings, watch technical changes | [loop-triage](.claude/skills/loop-triage/SKILL.md) · [tech-watch](.claude/skills/tech-watch/SKILL.md) |
| Keep feedback and improve the workflow | [pr-taste](.claude/skills/pr-taste/SKILL.md) · [retro](.claude/skills/retro/SKILL.md) |

[Full command reference →](command.md)

## Hooks

Agent hooks run inside your agent's session as soon as it loads the project's hooks. Claude Code
reads `.claude/settings.json`; the other agents get the same guards through their adapters
(`.codex/`, `.opencode/`, `.pi/`, `.dsh/`, `.devin/`, `.factory/`, `.agents/hooks.json`).

| Hook | Runs | What it does |
| --- | --- | --- |
| `session-start` | Session start, resume, compaction | Shows the mode, branch, git status, last commits, open tasks and repo health |
| `guard-bash` | Before every shell command | **Refuses** secret reads (also through globs, links and recursive searches), force-push, skipping git hooks, switching the flow mode, writing `.git/config`, Graphify's LLM commands, unbounded dumps |
| `guard-path` | Before Read, Edit, Write, Grep | **Refuses** secret files (a link judged by its target), writing `.git/config`, oversized reads |
| `after-edit` | After each edit | Runs the fast checks for the edited file and names the doc that covers it |
| `context-budget` | Every event | At 250k tokens: a verified handover, then compaction; at 300k, both are forced |
| `prompt-defense` | After web, MCP and network commands | Flags text that tries to steer the agent (advisory) |
| `token-feedback` | After tools, at stop | Flags tokens spent for nothing (advisory) |
| `sign-off` | When the agent stops (team flow) | Reminds once to update the covering docs and `work/current.md` |

Git hooks, once enabled: `pre-commit` runs the knowledge, link and repo-health checks;
`pre-push` (team flow) refuses `develop` without a recorded review and any direct push to `main`.
CI repeats the checks and scans for secrets, vulnerable dependencies and risky code on every pull
request and on pushes to `main` and `develop`.

## How the pieces fit

**Plan → define the change → build → test → review → release.** `AGENTS.md` sets the rules,
`knowledge/` holds the documentation, skills guide each task, hooks check the work as it happens,
and `work/current.md` tracks open tasks. You approve meaningful plans; the agent reports the checks
it actually ran.

## Documentation

| Looking for… | Read |
| --- | --- |
| Setup per agent, adoption, troubleshooting | [Getting started](knowledge/operations/getting-started.md) |
| Commands and skills | [Command reference](command.md) |
| The team flow, step by step | [Agent workflow](Workflow_Agent.md) |
| Policies, specs and decisions | [Knowledge index](knowledge/index.md) |
| Agent configuration and known limits | [Agent compatibility](knowledge/operations/agent-compatibility.md) |
| Tests and checks | [Build and test](knowledge/operations/build-and-test.md) |
| Context limits and handovers | [Context safety](knowledge/operations/context-safety.md) |
| The method, one phase at a time | [Seven-phase guide](efficiency/00-EFFICIENCY.md) |

Æion Factory works on its own. Optional [Æion modules](https://github.com/OualidGuennif/aeion-spaceship/blob/main/docs/modules.md)
cover security, audit, observability and integration.

[MIT license](LICENSE) · [Credits](CREDITS.md) · [Security](SECURITY.md)
