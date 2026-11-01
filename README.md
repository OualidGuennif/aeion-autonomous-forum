# Æion Factory

**Your coding agent. A clear workflow. Checks you can verify.**

A repository template that gives coding agents shared instructions, reusable skills,
linked documentation and automatic checks. Your project's rules and your decisions come first.

[Start](#start) · [Skills](#skills) · [Optional tools](#optional-tools) · [Your project](#use-it-for-your-project) · [Docs](#documentation)

## Start

You need **Git, Python 3.12+ and one installed, connected coding agent**.
Shell tools run on macOS, Linux and WSL.

```bash
git clone https://github.com/OualidGuennif/aeion-factory.git
cd aeion-factory
tools/agents/run claude
```

Replace `claude` with `codex`, `opencode`, `pi`, `dsh`, `devin`, `droid` or `antigravity`.
The launcher installs nothing. Trust the project and enable its hooks as your agent requires;
**Codex also needs its hooks enabled in `/hooks`, then a restart**.

[First-run setup and supported versions →](knowledge/operations/getting-started.md#prepare-your-agent)

## Skills

A skill is a reusable procedure your agent follows for a specific task.
Ask naturally: **“Use the plan skill to prepare this change.”**
Claude Code also accepts `/plan`; Codex accepts `$plan`.

| You want to… | Skills |
| --- | --- |
| Select task guidance without launching agents | [skill-route](.claude/skills/skill-route/SKILL.md) — native first, optional passive pool |
| Bring Æion Factory into an existing project | [adopt](.claude/skills/adopt/SKILL.md) |
| Plan a change and define its expected behaviour | [plan](.claude/skills/plan/SKILL.md) · [spec](.claude/skills/spec/SKILL.md) |
| Implement, test or fix a bug | [build](.claude/skills/build/SKILL.md) · [test](.claude/skills/test/SKILL.md) · [debug](.claude/skills/debug/SKILL.md) |
| Find dependencies, affected tests and specs | [graph](.claude/skills/graph/SKILL.md) — requires the optional architecture tools below |
| Update documentation and finish a task | [okf-update](.claude/skills/okf-update/SKILL.md) · [sign-off](.claude/skills/sign-off/SKILL.md) |
| Review and release | [pr-review](.claude/skills/pr-review/SKILL.md) · [ship](.claude/skills/ship/SKILL.md) |
| Check a sensitive change | [security-scan](.claude/skills/security-scan/SKILL.md) |
| Triage findings and evaluate technical changes | [loop-triage](.claude/skills/loop-triage/SKILL.md) · [tech-watch](.claude/skills/tech-watch/SKILL.md) |
| Keep useful feedback and improve the workflow | [pr-taste](.claude/skills/pr-taste/SKILL.md) · [retro](.claude/skills/retro/SKILL.md) |

[Full command reference →](command.md)

## Optional tools

**You can start without these.** Git hooks and Graphify/Archify are strongly recommended;
enable or install them when you choose.

| Option | What you get | Enable or install |
| --- | --- | --- |
| **Git hooks** | Checks before commits; a recorded review required before pushing `develop` | `git config core.hooksPath tools/git-hooks` |
| **Graphify + Archify** | Code dependency queries and architecture diagrams built from source, without an LLM | `python3 tools/architecture/setup` |
| **Claude Code plugins** | Optional hook, skill, security and language-server tools | [Plugin commands](command.md#plugins-claude-code) |
| **Scheduled loops** | Recurring checks, reviews and approved tasks; off until configured | [Loop setup](knowledge/operations/loops.md) |

Architecture setup creates a virtual environment with pinned packages and an Archify clone in
`.aeion/`. It also requires **Node 18+ and Chrome/Chromium**; Python 3.14.1 is excluded.
The agent must explain the installation and ask for your approval first. On a work computer,
check your company's installation policy.

[Architecture installation and first analysis →](knowledge/operations/architecture-maintenance.md)

## Use it for your project

### New project

[Create a repository from this template](https://github.com/OualidGuennif/aeion-factory/generate),
clone it, then run this **inside your new repository**:

```bash
tools/release/release --new-project
```

This resets the copied release metadata and task list. Ask your agent to adapt the instructions,
documentation and checks to your project before building features.

[New-project walkthrough and ready-to-copy prompt →](knowledge/operations/getting-started.md#new-project)

### Existing project

Open your project with your agent and use the **adopt** skill from a reference copy of Æion Factory.
It inspects what you have and proposes one phase at a time. Your instructions, tools and history stay
in place; you approve the changes before they are applied.

[Existing-project walkthrough and ready-to-copy prompt →](knowledge/operations/getting-started.md#existing-project)

### Just trying it locally?

Choose local mode in this clone to experiment without the team review and release workflow:

```bash
git config aeion.mode local
```

Safety guards still apply once your agent's hooks are loaded. To return to the default team workflow,
run `git config --unset aeion.mode`.

## How the pieces fit

**Plan → define the change → build → test → review → release.**

`AGENTS.md` sets the rules, `knowledge/` holds the documentation, skills guide each task,
and loaded hooks run checks during work. `work/current.md` tracks open tasks.
You approve meaningful plans; the agent reports the checks it actually ran.

The default team workflow uses `develop`, reviews before pushes, and a release pull request to
`main` after review and passing CI. [Follow the workflow →](Workflow_Agent.md)

## Documentation

| Looking for… | Read |
| --- | --- |
| Setup, adoption and dsh troubleshooting | [Getting started](knowledge/operations/getting-started.md) |
| Commands and skills | [Command reference](command.md) |
| Project policies, specs and decisions | [Knowledge index](knowledge/index.md) |
| Agent configuration, compatibility and known limits | [Agent compatibility](knowledge/operations/agent-compatibility.md) |
| Tests and checks | [Build and test](knowledge/operations/build-and-test.md) |
| Context limits and handovers | [Context safety](knowledge/operations/context-safety.md) |
| The method, one phase at a time | [Seven-phase guide](efficiency/00-EFFICIENCY.md) |

Æion Factory works independently. Optional [Æion modules](https://github.com/OualidGuennif/aeion-spaceship/blob/main/docs/modules.md)
cover security, audit, observability and integration.

[MIT license](LICENSE) · [Credits](CREDITS.md) · [Security](SECURITY.md)
