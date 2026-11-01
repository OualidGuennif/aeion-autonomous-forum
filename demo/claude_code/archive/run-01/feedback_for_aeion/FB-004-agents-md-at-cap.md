# FB-004: `AGENTS.md` ships at its 120-line cap but asks you to add lines

- **Severity:** medium (the first adoption step fails two checks)
- **Found in:** round 01
- **Status:** applied be08858

## Evidence
`AGENTS.md` is exactly 120 lines, and `knowledge-check` and `repo-health` fail above 120. It asks for two edits:
- "Replace this paragraph with what *your* project is";
- "Add your project's own build and test commands here, copied from CI".

When I followed both with 6 short lines, both checks failed:

```text
FAIL AGENTS.md: over 120 lines; route to docs instead of explaining     (knowledge-check)
FAIL AGENTS.md over 120 lines                                           (repo-health)
```

I fitted the app commands on one line and shortened my paragraph. The file can absorb a project's commands only by squeezing them, and a real project has more than two commands.

## Proposed fix
- Leave headroom in the template, about 10 lines. The seven lines of the factory's own test suite under "Build and test" can become one line: `tools/check-all`, or a pointer to the CI job that already lists them.
- Or have the cap count only the lines the project adds. That is harder to explain, so headroom is the better fix.

## Seen again (round 03, the parent repository)
The owner asked to map the new top-level `demo/` folder, and the sign-off hook asks for it too
("new top-level demo but AGENTS.md (repository map) did not change"). The parent `AGENTS.md` was
also at 120 lines, so a line had to go first: the two-line `loops/` entry became one line and lost
the list of loop names (that list lives in `knowledge/operations/loops.md`). Each new map line costs
an existing one.

## Seen again (round 06, the copy)
Adding the Docker commands to the copy's build line made `AGENTS.md` 121 lines, and both
`knowledge-check` and `repo-health` failed. The two app lines became one, and `deno task build &&
deno task start` moved to `app/README.md`, which the line now points to. This is the third time a
real, small addition had to evict something; a project's own build and test commands are exactly
what the template asks to add there ("Add your project's own build and test commands here").
