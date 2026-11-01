# FB-008: bounded intake calls a one-line read through a variable "a dump"

- **Severity:** low (one retry, and the refusal points at the wrong cause)
- **Found in:** round 03
- **Status:** applied 9d4f78c

## Evidence
After the smoke test I stopped the server with `kill $(cat $S/serve03.pid)`, where `$S` was the
scratchpad folder. `guard-bash` refused the whole command:

```text
Blocked by .claude/hooks/guard-bash.py: bounded intake: replace the dump with head -n 80, head -c 65536, ...
```

Fed to the hook one by one, the forms behave like this:

| Command | Result |
| --- | --- |
| `cat /tmp/x.pid` | allowed |
| `kill $(cat /tmp/x.pid) 2>/dev/null` | allowed |
| `cat $S/serve03.pid` | refused |
| `kill $(cat $S/serve03.pid)` | refused |

So the hook does not refuse `cat`: it refuses a `cat` whose path it cannot resolve, because it cannot
check that file's size. Failing closed is right. The message is not: it calls a one-line pid read a
dump, and that sent me looking at the wrong part of a ten-line command.

## Proposed fix
When the path contains a variable or a substitution, say so:
`bounded intake: cannot size "$S/serve03.pid" (variable path); use head -n 1 or a literal path`.
Add a test to `.claude/hooks/test_hooks.py` that checks this message.
