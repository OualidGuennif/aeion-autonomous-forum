# FB-001 — Environment-copy helper mistaken for an environment dump

- Severity: low; readiness: investigate; status: applied c3f586f.
- Round: 01, 2026-10-28.
- Observed: PreToolUse refused a Python validation helper containing
  `env=dict(os.environ,DENO_DIR=...)` and a later `print` of the child test's captured output.
  Reason: "it prints an environment, which holds keys". The helper never printed that mapping.
- Boundary: the parent Bash guard examines command text before execution. No secret was read
  or emitted; the blocked helper did not execute.
- Workaround: set only DENO_DIR in the shell and inherit the normal child environment. Remove
  the explicit environment-copy expression. This passed without changing the guard.
- Proposed fix: add a regression for separate environment-copy and output-print statements;
  investigate the detector's data-flow limits before narrowing it. Preserve actual dump refusals.
- Verification: synthetic coverage for both this benign helper and real environment dumps.
  No base-factory change made in this run.
