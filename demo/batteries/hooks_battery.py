#!/usr/bin/env python3
"""Feed real hook payloads to the factory copy's Claude hooks and report what each one decides.

Usage: python3 hooks_battery.py <factory-copy>
The copy's own hooks only fire in a session opened inside the copy; this battery runs them directly.
"""
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(sys.argv[1]).resolve()
APP = ROOT / "app"

# (hook, payload, expected exit code: 0 allowed, 2 refused, None informational)
CASES = [
    ("guard-path.py", {"tool_name": "Read", "tool_input": {"file_path": str(APP / ".env")}}, 2),
    ("guard-path.py", {"tool_name": "Read", "tool_input": {"file_path": str(APP / ".env.example")}}, 0),
    ("guard-path.py", {"tool_name": "Read", "tool_input": {"file_path": str(APP / "deno.json")}}, 0),
    ("guard-path.py", {"tool_name": "Read", "tool_input": {"file_path": str(APP / "data/ideas.sqlite3")}}, 0),
    ("guard-path.py", {"tool_name": "Grep", "tool_input": {"pattern": "KEY", "glob": "**/.env*"}}, 2),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "cat app/.env"}}, 2),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "cd app && deno test -A"}}, 0),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "docker compose exec web printenv"}}, 2),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "deno eval 'console.log(Deno.env.toObject())'"}}, 2),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "head -n 20 app/deno.json"}}, 0),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "cat app/deno.json"}}, None),
    ("guard-bash.py", {"tool_name": "Bash", "tool_input": {"command": "git init"}}, None),
    ("session-start.py", {"hook_event_name": "SessionStart", "source": "startup", "session_id": "demo"}, None),
    ("after-edit.py", {"tool_name": "Write", "tool_input": {"file_path": str(APP / "api/app.ts")}}, None),
    ("sign-off.py", {"hook_event_name": "Stop", "session_id": "demo", "stop_hook_active": False}, None),
]


def main():
    environment = dict(os.environ, CLAUDE_PROJECT_DIR=str(ROOT))
    failures = 0
    for hook, payload, expected in CASES:
        # Run from the copy, as a session opened there would; the hook path is relative to it, so the
        # command line holds no text from the caller.
        done = subprocess.run([sys.executable, f".claude/hooks/{hook}"], input=json.dumps(payload),
                              capture_output=True, text=True, cwd=ROOT, env=environment, timeout=30)
        said = " ".join((done.stdout + done.stderr).split())[:220]
        tool_input = payload.get("tool_input", {})
        subject = tool_input.get("command") or tool_input.get("file_path") or tool_input.get("glob") \
            or payload.get("hook_event_name")
        subject = str(subject).replace(str(ROOT) + "/", "")
        verdict = "info" if expected is None else ("PASS" if done.returncode == expected else "FAIL")
        failures += verdict == "FAIL"
        print(f"{verdict:4} {hook:16} exit={done.returncode} {subject!r} :: {said}")
    print(f"hooks battery: {failures} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
