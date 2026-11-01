#!/usr/bin/env python3
"""Re-check the factory's output independently of the factory.

The build worker already runs its own deterministic review, but a gate that only the party being
gated can run is not a gate. This script is the second, independent opinion: it enforces the same
contract on the pull request itself, so a compromised or buggy worker cannot land an artifact that
CI would have refused.

Contract for `demo/forum/product/`:
  * regular files only, no symlink;
  * at most 64 KiB per file and 1 MiB in total;
  * UTF-8 text, no NUL byte;
  * no credential-shaped string;
  * a Markdown artifact starts with a heading that names its feature id.

Exits 0 when the tree is absent or valid; prints one line per violation and exits 1 otherwise.
"""
import re
import sys
from pathlib import Path

PRODUCT = Path("demo/forum/product")
MAX_FILE = 64 * 1024
MAX_TOTAL = 1024 * 1024

SECRET_PATTERNS = (
    re.compile(r"\bgh[pousr]_[A-Za-z0-9]{20,}"),
    re.compile(r"\bgithub_pat_[A-Za-z0-9_]{20,}"),
    re.compile(r"\bsk-[A-Za-z0-9]{20,}"),
    re.compile(r"\bAKIA[0-9A-Z]{12,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{10,}"),
)
FEATURE_ID = re.compile(r"^(\d{3})[-_]")


def violations():
    if not PRODUCT.exists():
        print(f"{PRODUCT}: no product artifact yet, nothing to check")
        return []
    found, total = [], 0
    for path in sorted(PRODUCT.rglob("*")):
        if path.is_symlink():
            found.append(f"{path}: symlink refused")
            continue
        if not path.is_file():
            continue
        relative = path.relative_to(PRODUCT)
        size = path.stat().st_size
        total += size
        if size > MAX_FILE:
            found.append(f"{relative}: {size} bytes, limit {MAX_FILE}")
            continue
        data = path.read_bytes()
        if b"\0" in data:
            found.append(f"{relative}: binary content refused")
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            found.append(f"{relative}: not valid UTF-8")
            continue
        for pattern in SECRET_PATTERNS:
            if pattern.search(text):
                found.append(f"{relative}: credential-shaped string refused")
                break
        if path.suffix == ".md":
            first = next((line for line in text.splitlines() if line.strip()), "")
            if not first.startswith("# "):
                found.append(f"{relative}: Markdown artifact must start with a '# ' heading")
            match = FEATURE_ID.match(path.name)
            if match and match.group(1) not in first:
                found.append(f"{relative}: heading does not name feature {match.group(1)}")
    if total > MAX_TOTAL:
        found.append(f"{PRODUCT}: {total} bytes total, limit {MAX_TOTAL}")
    return found


def main():
    found = violations()
    for line in found:
        print(f"FAIL {line}")
    if found:
        print(f"{len(found)} violation(s) in {PRODUCT}")
        return 1
    print(f"{PRODUCT}: contract satisfied")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
