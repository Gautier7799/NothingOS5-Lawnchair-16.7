#!/usr/bin/env python3
"""GitHub handoff protocol engine.

A single-file, dependency-free CLI that turns the ``collaboration/`` directory
into the shared state hub between ChatGPT and OpenHands.

Design
------
* ``tasks/<TASK_ID>.json`` is the source of truth (one machine-readable file
  per task, named after the task id — so a duplicate id is a file collision).
* ``INBOX.md``, ``OUTBOX.md`` and ``CURRENT_STATUS.md`` are generated views.
  Never hand-edit them; run ``render``.
* ``ledger.jsonl`` is an append-only audit log of every state transition.
* A task cannot reach ``COMPLETED`` without ``evidence`` (command, result,
  commit sha). Enforced by the state machine, not by convention.
* Scope-changing or destructive actions are refused unless the task carries an
  explicit ``approval`` block.

Standard library only. No network access. No secrets are ever written: every
free-text field is scanned by :func:`scan_secrets` before it is persisted.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[2]
COLLAB_DIR = REPO_ROOT / "collaboration"
TASKS_DIR = COLLAB_DIR / "tasks"
REPORTS_DIR = COLLAB_DIR / "reports"
LEDGER = COLLAB_DIR / "ledger.jsonl"

# --------------------------------------------------------------------------
# Protocol constants
# --------------------------------------------------------------------------

STATES = (
    "PENDING",
    "ACCEPTED",
    "RUNNING",
    "REVIEW_REQUIRED",
    "COMPLETED",
    "BLOCKED",
)

#: Allowed transitions. A state may always move to BLOCKED.
TRANSITIONS: dict[str, set[str]] = {
    "PENDING": {"ACCEPTED", "BLOCKED"},
    "ACCEPTED": {"RUNNING", "BLOCKED", "PENDING"},
    "RUNNING": {"REVIEW_REQUIRED", "BLOCKED"},
    "REVIEW_REQUIRED": {"COMPLETED", "RUNNING", "BLOCKED"},
    "COMPLETED": set(),
    "BLOCKED": {"PENDING", "ACCEPTED"},
}

TASK_ID_RE = re.compile(r"^[A-Z][A-Z0-9]*-\d{3,}$")
MAX_ID_LEN = 64

RISK_CLASSES = ("read-only", "write", "destructive", "scope-change")

#: Risk classes that need an explicit approval block before execution.
APPROVAL_REQUIRED = {"destructive", "scope-change"}


class ProtocolError(Exception):
    """Raised for any protocol violation. Message is safe to show the user."""


# --------------------------------------------------------------------------
# Secret scanning
# --------------------------------------------------------------------------

#: Patterns that look like credentials. Deliberately broad; the cost of a
#: false positive (a refused write) is far lower than a committed key.
_SECRET_PATTERNS: tuple[tuple[str, re.Pattern[str]], ...] = (
    ("openhands_api_key", re.compile(r"\boh[-_][A-Za-z0-9]{16,}\b")),
    ("openai_key", re.compile(r"\bsk-[A-Za-z0-9_\-]{20,}\b")),
    ("github_token", re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{30,}\b")),
    ("github_pat", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{30,}\b")),
    ("slack_token", re.compile(r"\bxox[baprs]-[A-Za-z0-9\-]{10,}\b")),
    ("aws_access_key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("google_api_key", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b")),
    ("private_key_block", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    (
        "jwt",
        re.compile(r"\beyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{5,}\b"),
    ),
    (
        "bearer_literal",
        re.compile(r"\bBearer\s+[A-Za-z0-9_\-\.]{24,}", re.IGNORECASE),
    ),
    (
        "assigned_secret",
        re.compile(
            r"(?i)\b(api[_-]?key|secret|password|passwd|token|access[_-]?key)\b\s*[:=]\s*"
            r"[\"']?[A-Za-z0-9_\-/+]{16,}[\"']?"
        ),
    ),
)

#: Field names that must never appear in a persisted task document.
_FORBIDDEN_FIELDS = {
    "api_key",
    "apikey",
    "secret",
    "password",
    "passwd",
    "token",
    "access_token",
    "authorization",
    "credentials",
    "private_key",
}


def scan_secrets(text: str) -> list[str]:
    """Return the names of credential patterns found in ``text``.

    An empty list means clean. Callers must refuse to persist when this is
    non-empty; the matched values are never returned so they cannot leak into
    logs or error messages.
    """

    if not text:
        return []
    found: list[str] = []
    for name, pattern in _SECRET_PATTERNS:
        if pattern.search(text):
            found.append(name)
    return found


def _walk_strings(value: Any) -> list[str]:
    if isinstance(value, str):
        return [value]
    if isinstance(value, dict):
        out: list[str] = []
        for k, v in value.items():
            out.append(str(k))
            out.extend(_walk_strings(v))
        return out
    if isinstance(value, (list, tuple)):
        out = []
        for item in value:
            out.extend(_walk_strings(item))
        return out
    return []


def _check_document(doc: dict[str, Any]) -> None:
    """Refuse to persist a document that carries credentials."""

    for key in doc:
        if str(key).strip().lower() in _FORBIDDEN_FIELDS:
            raise ProtocolError(
                f"Refusing to persist field {key!r}: secret-bearing field names "
                "are not allowed in a task document."
            )
    hits = sorted({h for text in _walk_strings(doc) for h in scan_secrets(text)})
    if hits:
        raise ProtocolError(
            "Refusing to persist: content matches credential patterns "
            f"{hits}. Remove the secret and use an environment variable or a "
            "secret mount instead."
        )


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def task_path(task_id: str) -> Path:
    if not TASK_ID_RE.match(task_id) or len(task_id) > MAX_ID_LEN:
        raise ProtocolError(
            f"Invalid task id {task_id!r}. Expected the form AREA-NNN, for "
            "example COLLAB-001."
        )
    return TASKS_DIR / f"{task_id}.json"


def display_path(path: Path) -> str:
    """Path relative to the repo root when possible, else absolute.

    Tests point the engine at a temporary directory, so this must not assume
    the path is inside the repository.
    """

    try:
        return str(path.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


def git(*args: str) -> str:
    """Run a git command in the repository root. Returns stripped stdout."""

    try:
        out = subprocess.run(
            ("git", *args),
            cwd=REPO_ROOT,
            capture_output=True,
            text=True,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.SubprocessError):
        return ""
    return out.stdout.strip()


def current_branch() -> str:
    return git("rev-parse", "--abbrev-ref", "HEAD") or "unknown"


def current_commit() -> str:
    return git("rev-parse", "HEAD") or "unknown"


def load_task(task_id: str) -> dict[str, Any]:
    path = task_path(task_id)
    if not path.exists():
        raise ProtocolError(f"Unknown task id {task_id!r} (no {path.name}).")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ProtocolError(f"{path.name} is not valid JSON: {exc}") from exc


def save_task(doc: dict[str, Any]) -> Path:
    _check_document(doc)
    TASKS_DIR.mkdir(parents=True, exist_ok=True)
    path = task_path(doc["id"])
    path.write_text(
        json.dumps(doc, indent=2, sort_keys=False) + "\n", encoding="utf-8"
    )
    return path


def all_tasks() -> list[dict[str, Any]]:
    if not TASKS_DIR.is_dir():
        return []
    tasks: list[dict[str, Any]] = []
    for path in sorted(TASKS_DIR.glob("*.json")):
        try:
            tasks.append(json.loads(path.read_text(encoding="utf-8")))
        except json.JSONDecodeError:
            continue
    return tasks


def append_ledger(entry: dict[str, Any]) -> None:
    COLLAB_DIR.mkdir(parents=True, exist_ok=True)
    _check_document(entry)
    with LEDGER.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(entry, sort_keys=True) + "\n")


# --------------------------------------------------------------------------
# State machine
# --------------------------------------------------------------------------


@dataclass
class Transition:
    task_id: str
    to_state: str
    actor: str
    note: str = ""
    evidence: list[dict[str, str]] = field(default_factory=list)
    branch: str | None = None
    commit: str | None = None
    files: list[str] = field(default_factory=list)
    tests: list[str] = field(default_factory=list)
    artifacts: list[str] = field(default_factory=list)
    unresolved: list[str] = field(default_factory=list)


def _normalise_evidence(items: list[dict[str, str]]) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    for item in items:
        command = str(item.get("command", "")).strip()
        result = str(item.get("result", "")).strip()
        if not command or not result:
            raise ProtocolError(
                "Each evidence entry needs both 'command' and 'result'. "
                "Evidence is what makes a claim verifiable."
            )
        entry = {"command": command, "result": result}
        commit = str(item.get("commit", "")).strip()
        if commit:
            entry["commit"] = commit
        out.append(entry)
    return out


def apply_transition(t: Transition) -> dict[str, Any]:
    if t.to_state not in STATES:
        raise ProtocolError(
            f"Unknown state {t.to_state!r}. Valid states: {', '.join(STATES)}."
        )

    doc = load_task(t.task_id)
    previous = doc.get("status", "PENDING")

    if previous == t.to_state:
        raise ProtocolError(
            f"{t.task_id} is already {t.to_state}. Nothing to do (this is the "
            "duplicate-execution guard)."
        )
    allowed = TRANSITIONS.get(previous, set())
    if t.to_state not in allowed:
        raise ProtocolError(
            f"Illegal transition {previous} -> {t.to_state} for {t.task_id}. "
            f"Allowed from {previous}: {sorted(allowed) or 'none (terminal)'}."
        )

    risk = doc.get("risk", "read-only")
    evidence = _normalise_evidence(t.evidence)

    # Approval gate: destructive / scope-changing work needs a recorded approval.
    if t.to_state in {"RUNNING", "ACCEPTED"} and risk in APPROVAL_REQUIRED:
        approval = doc.get("approval") or {}
        if not approval.get("granted_by"):
            raise ProtocolError(
                f"{t.task_id} has risk={risk!r} and no approval block. Record "
                "one first:  collab approve <id> --by <name> --scope \"...\""
            )

    # Evidence gate: COMPLETED is impossible without verifiable evidence.
    if t.to_state == "COMPLETED":
        if not evidence:
            raise ProtocolError(
                f"Refusing to mark {t.task_id} COMPLETED without evidence. "
                "Provide at least one --evidence 'command=>result' entry."
            )
        if not doc.get("files_changed") and not t.files:
            raise ProtocolError(
                f"Refusing to mark {t.task_id} COMPLETED without a changed-file "
                "list. Pass --files or leave it empty explicitly as 'none'."
            )

    now = utc_now()
    doc["status"] = t.to_state
    doc["updated_at"] = now
    doc["updated_by"] = t.actor
    doc["branch"] = t.branch or doc.get("branch") or current_branch()
    if t.commit:
        doc["commit"] = t.commit
    if t.files:
        doc["files_changed"] = t.files
    if t.tests:
        doc["tests"] = t.tests
    if t.artifacts:
        doc["artifacts"] = t.artifacts
    if t.unresolved:
        doc["unresolved"] = t.unresolved
    if evidence:
        doc.setdefault("evidence", []).extend(evidence)

    history = doc.setdefault("history", [])
    history.append(
        {
            "at": now,
            "from": previous,
            "to": t.to_state,
            "by": t.actor,
            "note": t.note,
            "evidence_count": len(evidence),
        }
    )

    save_task(doc)
    append_ledger(
        {
            "at": now,
            "task_id": t.task_id,
            "from": previous,
            "to": t.to_state,
            "by": t.actor,
            "branch": doc["branch"],
            "commit": doc.get("commit", ""),
            "evidence_count": len(evidence),
        }
    )
    return doc


# --------------------------------------------------------------------------
# Rendering
# --------------------------------------------------------------------------


def _short_sha(sha: str | None) -> str:
    if not sha or sha == "unknown":
        return "—"
    return sha[:7]


def render_inbox(tasks: list[dict[str, Any]]) -> str:
    pending = [t for t in tasks if t.get("status") in {"PENDING", "BLOCKED"}]
    lines = [
        "# INBOX — tasks for OpenHands",
        "",
        "Generated file. Do not hand-edit; run `collab render`.",
        "",
        "How a task gets here: ChatGPT (or a human) creates the task, then",
        "triggers OpenHands on GitHub by adding the `openhands` label to the",
        "tracking issue or commenting `@openhands` on it. OpenHands then claims",
        "the task with `collab accept`, which moves it to ACCEPTED.",
        "",
        f"Open tasks: **{len(pending)}**",
        "",
        "| Task | Status | Risk | Title | Issue | Updated |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for task in pending:
        issue = task.get("issue")
        issue_cell = f"[#{issue}]({task.get('issue_url', '')})" if issue else "—"
        lines.append(
            f"| `{task['id']}` | {task.get('status')} | {task.get('risk', '—')} | "
            f"{task.get('title', '')} | {issue_cell} | {task.get('updated_at', '—')} |"
        )
    if not pending:
        lines.append("| — | — | — | _No open tasks_ | — | — |")
    lines += [
        "",
        "## Claim protocol",
        "",
        "```bash",
        "python3 collaboration/tools/collab.py list --status PENDING",
        "python3 collaboration/tools/collab.py accept <TASK_ID> --actor openhands",
        "python3 collaboration/tools/collab.py start  <TASK_ID> --actor openhands",
        "```",
        "",
        "Claiming is idempotent-safe: the first `accept` wins, and a second",
        "`accept` on the same task is refused, so two agents cannot run the same",
        "task twice.",
        "",
    ]
    return "\n".join(lines)


def render_outbox(tasks: list[dict[str, Any]]) -> str:
    relevant = [t for t in tasks if t.get("status") in {"REVIEW_REQUIRED", "COMPLETED"}]
    lines = [
        "# OUTBOX — results for ChatGPT to review",
        "",
        "Generated file. Do not hand-edit; run `collab render`.",
        "",
        "A task appears here once OpenHands has produced evidence. Nothing is",
        "marked COMPLETED without a command/result pair, so every claim below is",
        "checkable.",
        "",
        f"Items awaiting review or done: **{len(relevant)}**",
        "",
        "| Task | Status | Commit | Report | Unresolved |",
        "| --- | --- | --- | --- | --- |",
    ]
    for task in relevant:
        report = task.get("report")
        report_cell = (
            f"[{report}]({_relative_report_url(report)})" if report else "—"
        )
        unresolved = task.get("unresolved") or []
        lines.append(
            f"| `{task['id']}` | {task.get('status')} | "
            f"`{_short_sha(task.get('commit'))}` | {report_cell} | "
            f"{len(unresolved)} |"
        )
    if not relevant:
        lines.append("| — | — | — | _No results yet_ | — |")

    evidence_tasks = [t for t in relevant if t.get("evidence")]
    if evidence_tasks:
        lines += ["", "## Evidence", ""]
        for task in evidence_tasks:
            lines.append(f"### `{task['id']}` — {task.get('title', '')}")
            lines.append("")
            for item in task["evidence"]:
                lines.append(f"- `{item.get('command')}`")
                lines.append(f"  - result: {item.get('result')}")
                if item.get("commit"):
                    lines.append(f"  - commit: `{item['commit']}`")
            lines.append("")

    lines += [
        "## Review actions",
        "",
        "```bash",
        "# Approve and close",
        "python3 collaboration/tools/collab.py complete <TASK_ID> \\",
        "  --actor chatgpt --note 'reviewed' --evidence 'review=>accepted'",
        "",
        "# Send back for more work",
        "python3 collaboration/tools/collab.py transition <TASK_ID> RUNNING \\",
        "  --actor chatgpt --note 'needs changes: ...'",
        "```",
        "",
    ]
    return "\n".join(lines)


def render_status(tasks: list[dict[str, Any]]) -> str:
    counts = {state: 0 for state in STATES}
    for task in tasks:
        state = task.get("status")
        if state in counts:
            counts[state] += 1

    lines = [
        "# CURRENT_STATUS — shared project state",
        "",
        "Generated file. Do not hand-edit; run `collab render`.",
        "",
        f"- Generated: `{utc_now()}`",
        f"- Repository: `{git('config', '--get', 'remote.origin.url') or 'unknown'}`",
        f"- Branch: `{current_branch()}`",
        f"- HEAD: `{current_commit()}`",
        f"- Total tasks: **{len(tasks)}**",
        "",
        "## Board",
        "",
        "| State | Count |",
        "| --- | --- |",
    ]
    for state in STATES:
        lines.append(f"| {state} | {counts[state]} |")

    lines += [
        "",
        "## All tasks",
        "",
        "| Task | Status | Risk | Branch | Commit | Updated |",
        "| --- | --- | --- | --- | --- | --- |",
    ]
    for task in tasks:
        lines.append(
            f"| `{task['id']}` | {task.get('status')} | {task.get('risk', '—')} | "
            f"`{task.get('branch', '—')}` | `{_short_sha(task.get('commit'))}` | "
            f"{task.get('updated_at', '—')} |"
        )
    if not tasks:
        lines.append("| — | — | — | — | — | — |")

    lines += [
        "",
        "## MCP bridge health",
        "",
        "The read-only bridge is unaffected by this protocol. Its tests must",
        "keep passing; see `mcp-bridge/docs/VERIFICATION.md`.",
        "",
    ]
    return "\n".join(lines)


def _relative_report_url(report: str) -> str:
    return f"reports/{report}" if not report.startswith("reports/") else report


def render_all() -> list[Path]:
    tasks = all_tasks()
    written: list[Path] = []
    for name, content in (
        ("INBOX.md", render_inbox(tasks)),
        ("OUTBOX.md", render_outbox(tasks)),
        ("CURRENT_STATUS.md", render_status(tasks)),
    ):
        path = COLLAB_DIR / name
        path.write_text(content, encoding="utf-8")
        written.append(path)
    return written


# --------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------


def cmd_create(args: argparse.Namespace) -> int:
    path = task_path(args.task_id)
    if path.exists():
        existing = json.loads(path.read_text(encoding="utf-8"))
        raise ProtocolError(
            f"Task {args.task_id} already exists with status "
            f"{existing.get('status')}. Duplicate ids are refused so a task can "
            "never be executed twice."
        )

    evidence = _normalise_evidence(args.evidence or [])
    doc: dict[str, Any] = {
        "id": args.task_id,
        "title": args.title,
        "status": "PENDING",
        "risk": args.risk,
        "owner": args.owner,
        "created_at": utc_now(),
        "updated_at": utc_now(),
        "updated_by": args.owner,
        "branch": args.branch or current_branch(),
        "commit": args.commit or "",
        "objective": args.objective or "",
        "acceptance": args.acceptance or [],
        "constraints": args.constraints or [],
        "files_changed": [],
        "tests": [],
        "artifacts": [],
        "unresolved": [],
        "evidence": evidence,
        "history": [
            {
                "at": utc_now(),
                "from": None,
                "to": "PENDING",
                "by": args.owner,
                "note": "created",
                "evidence_count": len(evidence),
            }
        ],
    }
    if args.issue:
        doc["issue"] = args.issue
        doc["issue_url"] = args.issue_url or ""
    if args.risk in APPROVAL_REQUIRED:
        doc["approval"] = {"granted_by": None, "scope": None, "at": None}

    saved = save_task(doc)
    append_ledger(
        {
            "at": doc["created_at"],
            "task_id": doc["id"],
            "from": None,
            "to": "PENDING",
            "by": args.owner,
            "branch": doc["branch"],
            "commit": "",
            "evidence_count": len(evidence),
        }
    )
    render_all()
    print(f"created {display_path(saved)} (status PENDING)")
    return 0


def cmd_approve(args: argparse.Namespace) -> int:
    doc = load_task(args.task_id)
    if not args.by:
        raise ProtocolError("--by is required: an approval must name a human.")
    doc["approval"] = {"granted_by": args.by, "scope": args.scope, "at": utc_now()}
    doc["updated_at"] = utc_now()
    doc["updated_by"] = args.by
    doc.setdefault("history", []).append(
        {
            "at": doc["updated_at"],
            "from": doc["status"],
            "to": doc["status"],
            "by": args.by,
            "note": f"approval granted: {args.scope}",
            "evidence_count": 0,
        }
    )
    save_task(doc)
    append_ledger(
        {
            "at": doc["updated_at"],
            "task_id": args.task_id,
            "from": doc["status"],
            "to": doc["status"],
            "by": args.by,
            "branch": doc.get("branch", ""),
            "commit": doc.get("commit", ""),
            "evidence_count": 0,
        }
    )
    print(f"approved {args.task_id} by {args.by}: {args.scope}")
    return 0


def _parse_evidence(items: list[str] | None) -> list[dict[str, str]]:
    out: list[dict[str, str]] = []
    for raw in items or []:
        if "=>" not in raw:
            raise ProtocolError(
                f"Evidence {raw!r} must look like 'command=>result'."
            )
        command, result = raw.split("=>", 1)
        out.append({"command": command.strip(), "result": result.strip()})
    return out


def _parse_list(values: list[str] | None) -> list[str]:
    out: list[str] = []
    for raw in values or []:
        out.extend(part.strip() for part in raw.split(",") if part.strip())
    return out


def cmd_transition(args: argparse.Namespace) -> int:
    doc = apply_transition(
        Transition(
            task_id=args.task_id,
            to_state=args.state,
            actor=args.actor,
            note=args.note or "",
            evidence=_parse_evidence(args.evidence),
            branch=args.branch,
            commit=args.commit,
            files=_parse_list(args.files),
            tests=_parse_list(args.tests),
            artifacts=_parse_list(args.artifacts),
            unresolved=_parse_list(args.unresolved),
        )
    )
    render_all()
    print(f"{doc['id']} -> {doc['status']}")
    return 0


def cmd_accept(args: argparse.Namespace) -> int:
    args.state = "ACCEPTED"
    return cmd_transition(args)


def cmd_start(args: argparse.Namespace) -> int:
    args.state = "RUNNING"
    return cmd_transition(args)


def cmd_review(args: argparse.Namespace) -> int:
    args.state = "REVIEW_REQUIRED"
    return cmd_transition(args)


def cmd_block(args: argparse.Namespace) -> int:
    args.state = "BLOCKED"
    return cmd_transition(args)


def cmd_complete(args: argparse.Namespace) -> int:
    args.state = "COMPLETED"
    return cmd_transition(args)


def cmd_list(args: argparse.Namespace) -> int:
    tasks = all_tasks()
    if args.status:
        wanted = {s.upper() for s in args.status}
        tasks = [t for t in tasks if t.get("status") in wanted]
    if args.json:
        print(json.dumps(tasks, indent=2))
    else:
        if not tasks:
            print("(no tasks)")
        for task in tasks:
            print(
                f"{task['id']:14} {task.get('status', '?'):16} "
                f"{task.get('risk', '?'):12} {task.get('title', '')}"
            )
    return 0


def cmd_show(args: argparse.Namespace) -> int:
    print(json.dumps(load_task(args.task_id), indent=2))
    return 0


def cmd_render(args: argparse.Namespace) -> int:
    for path in render_all():
        print(f"wrote {display_path(path)}")
    return 0


def cmd_scan(args: argparse.Namespace) -> int:
    """Scan the collaboration tree (and optionally the repo) for secrets."""

    roots = [COLLAB_DIR]
    if args.all:
        roots = [REPO_ROOT]
    hits = 0
    checked = 0
    for root in roots:
        for path in sorted(root.rglob("*")):
            if not path.is_file():
                continue
            if any(part in {".git", ".venv", "__pycache__", "dist"} for part in path.parts):
                continue
            if path.suffix in {".zip", ".pyc", ".png", ".jpg", ".pdf"}:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            checked += 1
            found = scan_secrets(text)
            if found:
                hits += 1
                print(f"SECRET  {display_path(path)}: {found}")
    print(f"scanned {checked} files, {hits} with credential patterns")
    return 1 if hits else 0


def cmd_verify(args: argparse.Namespace) -> int:
    """Integrity check used by CI and by the human reviewer."""

    problems: list[str] = []
    tasks = all_tasks()

    seen_ids: set[str] = set()
    for task in tasks:
        tid = task.get("id", "<missing>")
        if tid in seen_ids:
            problems.append(f"duplicate task id {tid}")
        seen_ids.add(tid)

        if task.get("status") not in STATES:
            problems.append(f"{tid}: invalid status {task.get('status')!r}")
        if task.get("status") == "COMPLETED" and not task.get("evidence"):
            problems.append(f"{tid}: COMPLETED without evidence")
        if task.get("status") == "COMPLETED" and not task.get("files_changed"):
            problems.append(f"{tid}: COMPLETED without files_changed")
        if task.get("risk") in APPROVAL_REQUIRED:
            approval = task.get("approval") or {}
            if task.get("status") in {"RUNNING", "REVIEW_REQUIRED", "COMPLETED"} and not (
                approval.get("granted_by")
            ):
                problems.append(
                    f"{tid}: risk={task.get('risk')} advanced without approval"
                )
        report = task.get("report")
        if report and not (REPORTS_DIR / report).exists():
            problems.append(f"{tid}: report {report!r} not found in reports/")

    # Ledger and task files must agree on the latest state.
    if LEDGER.exists():
        latest: dict[str, str] = {}
        for line in LEDGER.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                problems.append("ledger.jsonl contains a malformed line")
                continue
            latest[entry["task_id"]] = entry["to"]
        for task in tasks:
            tid = task.get("id")
            if tid in latest and latest[tid] != task.get("status"):
                problems.append(
                    f"{tid}: ledger says {latest[tid]} but task says {task.get('status')}"
                )

    if problems:
        print("VERIFY FAILED")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(f"VERIFY OK ({len(tasks)} tasks, ledger consistent)")
    return 0


def cmd_fingerprint(args: argparse.Namespace) -> int:
    """Stable digest of task state, for cheap change detection by a client."""

    tasks = all_tasks()
    blob = json.dumps(
        [
            {
                "id": t.get("id"),
                "status": t.get("status"),
                "commit": t.get("commit"),
                "updated_at": t.get("updated_at"),
            }
            for t in tasks
        ],
        sort_keys=True,
    ).encode("utf-8")
    print(hashlib.sha256(blob).hexdigest())
    return 0


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="collab",
        description="GitHub handoff protocol engine (offline, no secrets).",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("create", help="create a new task (refuses duplicate ids)")
    p.add_argument("task_id")
    p.add_argument("--title", required=True)
    p.add_argument("--owner", default="chatgpt")
    p.add_argument("--risk", choices=RISK_CLASSES, default="read-only")
    p.add_argument("--objective", default="")
    p.add_argument("--acceptance", action="append")
    p.add_argument("--constraints", action="append")
    p.add_argument("--issue", type=int)
    p.add_argument("--issue-url", default="")
    p.add_argument("--branch")
    p.add_argument("--commit")
    p.add_argument("--evidence", action="append", help="'command=>result'")
    p.set_defaults(func=cmd_create)

    p = sub.add_parser("approve", help="record explicit approval for risky work")
    p.add_argument("task_id")
    p.add_argument("--by", required=True)
    p.add_argument("--scope", default="")
    p.set_defaults(func=cmd_approve)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("task_id")
    common.add_argument("--actor", default="openhands")
    common.add_argument("--note", default="")
    common.add_argument("--evidence", action="append")
    common.add_argument("--branch")
    common.add_argument("--commit")
    common.add_argument("--files", action="append")
    common.add_argument("--tests", action="append")
    common.add_argument("--artifacts", action="append")
    common.add_argument("--unresolved", action="append")

    for name, func, helptext in (
        ("accept", cmd_accept, "PENDING -> ACCEPTED (claims the task)"),
        ("start", cmd_start, "ACCEPTED -> RUNNING"),
        ("review", cmd_review, "RUNNING -> REVIEW_REQUIRED"),
        ("block", cmd_block, "any -> BLOCKED"),
        ("complete", cmd_complete, "REVIEW_REQUIRED -> COMPLETED (needs evidence)"),
    ):
        p = sub.add_parser(name, parents=[common], help=helptext)
        p.set_defaults(func=func)

    p = sub.add_parser(
        "transition", parents=[common], help="explicit transition to any state"
    )
    p.add_argument("state")
    p.set_defaults(func=cmd_transition)

    p = sub.add_parser("list", help="list tasks")
    p.add_argument("--status", action="append")
    p.add_argument("--json", action="store_true")
    p.set_defaults(func=cmd_list)

    p = sub.add_parser("show", help="dump one task as JSON")
    p.add_argument("task_id")
    p.set_defaults(func=cmd_show)

    p = sub.add_parser("render", help="regenerate INBOX/OUTBOX/CURRENT_STATUS")
    p.set_defaults(func=cmd_render)

    p = sub.add_parser("scan", help="scan for credential patterns")
    p.add_argument("--all", action="store_true", help="scan the whole repository")
    p.set_defaults(func=cmd_scan)

    p = sub.add_parser("verify", help="integrity check (evidence, approval, ledger)")
    p.set_defaults(func=cmd_verify)

    p = sub.add_parser("fingerprint", help="sha256 of task state")
    p.set_defaults(func=cmd_fingerprint)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except ProtocolError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
