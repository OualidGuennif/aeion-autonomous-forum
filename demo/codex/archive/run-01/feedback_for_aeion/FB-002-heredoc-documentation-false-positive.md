# FB-002 — Documentation heredoc mistaken for a secret read

- Severity: low; status: applied c3f586f; round: 06, 2026-10-28.
- Observed: the parent Bash guard refused `cat > knowledge/components/idea-board-runtime.md`
  with a quoted heredoc containing the prose ".env files, local data and dependencies are excluded".
  It reported "it reads a secret file (.env)". This was writing a spec, not opening a secret.
- Workaround: use apply_patch for the documentation and Docker context exclusions. No guard change.
- Proposed fix: distinguish quoted heredoc data from shell read operands in the Bash detector.
  Add this benign case alongside actual `cat` secret-read refusals before changing the parser.
- Verification: documentation writes should pass; actual secret reads must still fail. No base fix.
