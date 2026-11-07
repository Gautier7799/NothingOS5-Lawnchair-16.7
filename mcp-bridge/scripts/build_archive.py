#!/usr/bin/env python3
"""Build the deliverable ZIP: complete, self-contained source archive.

Excludes virtualenvs, caches, build output, and every secret-bearing path. The
archive is written to ``dist/openhands-mcp-bridge-1.0.0.zip``.
"""

from __future__ import annotations

import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
ARCHIVE = DIST / "openhands-mcp-bridge-1.0.0.zip"

INCLUDE_TOP_LEVEL = [
    "README.md",
    "LICENSE",
    "pyproject.toml",
    "Dockerfile",
    "docker-compose.yml",
    "Makefile",
    ".env.example",
    ".gitignore",
    ".dockerignore",
    "FINAL_HANDOFF_FOR_CHATGPT.txt",
]
INCLUDE_TREES = ["src", "tests", "docs", "deploy", "scripts"]

EXCLUDED_DIR_NAMES = {
    ".venv",
    "venv",
    "__pycache__",
    ".pytest_cache",
    ".ruff_cache",
    ".mypy_cache",
    ".git",
    "dist",
    "build",
    "secrets",
    ".eggs",
    "node_modules",
}
EXCLUDED_SUFFIXES = {".pyc", ".pyo", ".pem", ".key", ".zip", ".log"}
EXCLUDED_NAMES = {".env", ".coverage"}


def _is_excluded(path: pathlib.Path) -> bool:
    if path.name in EXCLUDED_NAMES:
        return True
    if path.suffix in EXCLUDED_SUFFIXES:
        return True
    if path.name.endswith(".egg-info"):
        return True
    return any(part in EXCLUDED_DIR_NAMES for part in path.parts)


def _iter_tree(root: pathlib.Path):
    for path in sorted(root.rglob("*")):
        if path.is_file() and not _is_excluded(path):
            yield path


def main() -> int:
    DIST.mkdir(exist_ok=True)
    if ARCHIVE.exists():
        ARCHIVE.unlink()

    added: list[str] = []
    with zipfile.ZipFile(ARCHIVE, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name in INCLUDE_TOP_LEVEL:
            path = ROOT / name
            if path.is_file():
                archive.write(path, arcname=name)
                added.append(name)

        for tree in INCLUDE_TREES:
            base = ROOT / tree
            if not base.is_dir():
                continue
            for path in _iter_tree(base):
                arcname = str(path.relative_to(ROOT))
                archive.write(path, arcname=arcname)
                added.append(arcname)

    size_kb = ARCHIVE.stat().st_size / 1024
    print(f"wrote {ARCHIVE} ({len(added)} files, {size_kb:.1f} KiB)")
    for entry in added:
        print(f"  {entry}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
