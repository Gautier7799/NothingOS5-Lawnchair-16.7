#!/usr/bin/env python3
"""Build the collaboration deliverable archive.

Produces ``dist/collaboration-hub-1.0.0.zip`` containing the collaboration
protocol, its engine, tests and docs, plus the issue template and the optional
CI workflow. No secrets, no virtualenv, no build output, no caches.

The archive includes a MANIFEST.txt with sizes and SHA-256 digests so the
recipient can verify what they received.

Run:  python3 collaboration/tools/build_archive.py
"""

from __future__ import annotations

import hashlib
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "dist"
ARCHIVE = DIST / "collaboration-hub-1.0.0.zip"

#: (source path, path inside the archive)
MEMBERS: list[tuple[str, str]] = [
    ("collaboration/README.md", "collaboration/README.md"),
    ("collaboration/PROTOCOL.md", "collaboration/PROTOCOL.md"),
    ("collaboration/ARCHITECTURE.md", "collaboration/ARCHITECTURE.md"),
    ("collaboration/PROJECT_CONTEXT.md", "collaboration/PROJECT_CONTEXT.md"),
    ("collaboration/INBOX.md", "collaboration/INBOX.md"),
    ("collaboration/OUTBOX.md", "collaboration/OUTBOX.md"),
    ("collaboration/CURRENT_STATUS.md", "collaboration/CURRENT_STATUS.md"),
    ("collaboration/ledger.jsonl", "collaboration/ledger.jsonl"),
    ("collaboration/.gitignore", "collaboration/.gitignore"),
    ("collaboration/tools/collab.py", "collaboration/tools/collab.py"),
    (
        "collaboration/tools/build_archive.py",
        "collaboration/tools/build_archive.py",
    ),
    ("collaboration/tests/test_collab.py", "collaboration/tests/test_collab.py"),
    ("collaboration/reports/TEMPLATE.md", "collaboration/reports/TEMPLATE.md"),
    (
        "collaboration/reports/COLLAB-001.md",
        "collaboration/reports/COLLAB-001.md",
    ),
    (
        "collaboration/tasks/COLLAB-001.json",
        "collaboration/tasks/COLLAB-001.json",
    ),
    (
        ".github/workflows/collaboration-verify.yml",
        ".github/workflows/collaboration-verify.yml",
    ),
    (
        ".github/ISSUE_TEMPLATE/openhands-task.yml",
        ".github/ISSUE_TEMPLATE/openhands-task.yml",
    ),
    ("AGENTS.md", "AGENTS.md"),
    (
        "mcp-bridge/FINAL_HANDOFF_FOR_CHATGPT.txt",
        "mcp-bridge/FINAL_HANDOFF_FOR_CHATGPT.txt",
    ),
]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def main() -> int:
    DIST.mkdir(parents=True, exist_ok=True)

    missing = [src for src, _ in MEMBERS if not (ROOT / src).exists()]
    if missing:
        print("missing required files:")
        for name in missing:
            print(f"  - {name}")
        return 1

    lines = [
        "collaboration-hub archive manifest",
        f"generated: {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}",
        "",
        f"{'size':>10}  sha256                                                            path",
    ]
    total = 0
    with zipfile.ZipFile(ARCHIVE, "w", zipfile.ZIP_DEFLATED) as zf:
        for src, arcname in MEMBERS:
            path = ROOT / src
            data = path.read_bytes()
            total += len(data)
            zf.writestr(arcname, data)
            lines.append(f"{len(data):>10}  {sha256(path)}  {arcname}")

    manifest = "\n".join(lines) + "\n"
    with zipfile.ZipFile(ARCHIVE, "a", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("MANIFEST.txt", manifest)

    size = ARCHIVE.stat().st_size
    print(f"wrote {ARCHIVE.relative_to(ROOT)} "
          f"({len(MEMBERS) + 1} entries, {size / 1024:.1f} KiB)")
    print(f"sha256 {sha256(ARCHIVE)}")
    print(f"uncompressed payload: {total / 1024:.1f} KiB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
