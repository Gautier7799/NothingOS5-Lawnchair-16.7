#!/usr/bin/env python3
"""Tests for the collaboration protocol engine.

Run:  python3 collaboration/tests/test_collab.py

Standard library only, no network. Each test isolates itself by pointing the
engine at a temporary directory, so the real ``collaboration/`` tree is never
touched by the suite.
"""

from __future__ import annotations

import importlib.util
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ENGINE = REPO_ROOT / "collaboration" / "tools" / "collab.py"


def load_engine():
    spec = importlib.util.spec_from_file_location("collab_engine", ENGINE)
    module = importlib.util.module_from_spec(spec)
    sys.modules["collab_engine"] = module
    spec.loader.exec_module(module)
    return module


class SandboxedCase(unittest.TestCase):
    """Redirects the engine's module-level paths into a temp directory."""

    def setUp(self) -> None:
        self.engine = load_engine()
        self._tmp = Path(tempfile.mkdtemp(prefix="collab-test-"))
        collab_dir = self._tmp / "collaboration"
        (collab_dir / "tasks").mkdir(parents=True)
        (collab_dir / "reports").mkdir(parents=True)

        self.engine.COLLAB_DIR = collab_dir
        self.engine.TASKS_DIR = collab_dir / "tasks"
        self.engine.REPORTS_DIR = collab_dir / "reports"
        self.engine.LEDGER = collab_dir / "ledger.jsonl"

    def tearDown(self) -> None:
        shutil.rmtree(self._tmp, ignore_errors=True)

    def run_cli(self, *args: str) -> int:
        return self.engine.main(list(args))

    def create(self, task_id: str = "COLLAB-001", risk: str = "read-only", **kw):
        args = ["create", task_id, "--title", kw.pop("title", "demo task")]
        if risk:
            args += ["--risk", risk]
        for key, value in kw.items():
            args += [f"--{key.replace('_', '-')}", str(value)]
        return self.run_cli(*args)

    def task(self, task_id: str = "COLLAB-001") -> dict:
        return self.engine.load_task(task_id)


class TestSecretScanning(SandboxedCase):
    def test_clean_text_has_no_hits(self):
        self.assertEqual(self.engine.scan_secrets("a normal sentence"), [])

    def test_detects_common_credential_shapes(self):
        # Samples are assembled from fragments so this test file contains no
        # literal credential pattern and therefore passes `collab scan` itself.
        cases = {
            "openhands_api_key": "oh-" + "abcdefghijklmnopqrstuvwx",
            "openai_key": "sk" + "-" + "a" * 30,
            "github_token": "gh" + "p_" + "b" * 36,
            "github_pat": "github" + "_pat_" + "c" * 40,
            "slack_token": "xox" + "b-1234567890-abcdefghij",
            "aws_access_key": "AKIA" + "IOSFODNN7EXAMPLE",
            "google_api_key": "AIza" + "d" * 35,
            "private_key_block": "-----BEGIN RSA " + "PRIVATE KEY-----",
            "jwt": "eyJhbGciOiJIUzI1NiJ9." + "eyJzdWIiOiIxIn0." + "abcdefghij",
            "bearer_literal": "Bear" + "er " + "e" * 30,
            "assigned_secret": 'api_' + 'key = "0123456789abcdefghij"',
        }
        for expected, sample in cases.items():
            with self.subTest(pattern=expected):
                self.assertIn(expected, self.engine.scan_secrets(sample))

    def test_create_refuses_a_document_containing_a_secret(self):
        code = self.create(objective="deploy with " + "sk-" + "f" * 30)
        self.assertEqual(code, 2, "a secret-bearing task must be refused")
        self.assertFalse((self.engine.TASKS_DIR / "COLLAB-001.json").exists())

    def test_refusal_message_does_not_echo_the_secret(self):
        secret = "sk-" + "g" * 30
        with self.assertRaises(self.engine.ProtocolError) as ctx:
            self.engine.save_task({"id": "COLLAB-001", "objective": secret})
        self.assertNotIn(secret, str(ctx.exception))

    def test_forbidden_field_names_are_rejected(self):
        with self.assertRaises(self.engine.ProtocolError):
            self.engine.save_task({"id": "COLLAB-001", "api_" + "key": "anything"})

    def test_nested_secrets_are_caught(self):
        doc = {
            "id": "COLLAB-001",
            "evidence": [
                {"command": "curl", "result": "token: " + "sk-" + "h" * 30}
            ],
        }
        with self.assertRaises(self.engine.ProtocolError):
            self.engine.save_task(doc)


class TestTaskIdentityAndDuplicates(SandboxedCase):
    def test_create_then_duplicate_is_refused(self):
        self.assertEqual(self.create(), 0)
        self.assertEqual(self.create(), 2, "duplicate id must be refused")
        self.assertEqual(self.task()["status"], "PENDING")

    def test_invalid_ids_are_rejected(self):
        for bad in ("collab-001", "COLLAB", "COLLAB-1", "../etc/passwd", "A" * 70):
            with self.subTest(task_id=bad):
                self.assertEqual(self.create(bad), 2)

    def test_valid_id_shapes_accepted(self):
        for good in ("COLLAB-001", "BRIDGE-014", "A-100", "X9-999"):
            with self.subTest(task_id=good):
                self.assertEqual(self.create(good), 0)

    def test_file_name_equals_task_id(self):
        self.create("BRIDGE-007")
        self.assertTrue((self.engine.TASKS_DIR / "BRIDGE-007.json").exists())


class TestStateMachine(SandboxedCase):
    def setUp(self):
        super().setUp()
        self.create()

    def test_happy_path(self):
        self.assertEqual(self.run_cli("accept", "COLLAB-001"), 0)
        self.assertEqual(self.task()["status"], "ACCEPTED")
        self.assertEqual(self.run_cli("start", "COLLAB-001"), 0)
        self.assertEqual(self.task()["status"], "RUNNING")
        self.assertEqual(self.run_cli("review", "COLLAB-001"), 0)
        self.assertEqual(self.task()["status"], "REVIEW_REQUIRED")
        self.assertEqual(
            self.run_cli(
                "complete", "COLLAB-001", "--evidence", "pytest=>83 passed",
                "--files", "collaboration/PROTOCOL.md",
            ),
            0,
        )
        self.assertEqual(self.task()["status"], "COMPLETED")

    def test_illegal_transition_is_refused(self):
        # PENDING -> RUNNING is not allowed.
        self.assertEqual(self.run_cli("start", "COLLAB-001"), 2)
        self.assertEqual(self.task()["status"], "PENDING")

    def test_completed_is_terminal(self):
        self.run_cli("accept", "COLLAB-001")
        self.run_cli("start", "COLLAB-001")
        self.run_cli("review", "COLLAB-001")
        self.run_cli(
            "complete", "COLLAB-001", "--evidence", "e=>r", "--files", "x"
        )
        self.assertEqual(self.run_cli("transition", "COLLAB-001", "RUNNING"), 2)

    def test_repeating_the_same_state_is_refused(self):
        self.run_cli("accept", "COLLAB-001")
        self.assertEqual(self.run_cli("accept", "COLLAB-001"), 2)

    def test_blocked_can_recover(self):
        self.assertEqual(self.run_cli("block", "COLLAB-001", "--note", "waiting"), 0)
        self.assertEqual(self.task()["status"], "BLOCKED")
        self.assertEqual(self.run_cli("accept", "COLLAB-001"), 0)
        self.assertEqual(self.task()["status"], "ACCEPTED")

    def test_every_state_is_reachable_from_pending(self):
        self.run_cli("block", "COLLAB-001")
        self.assertEqual(self.task()["status"], "BLOCKED")
        self.run_cli("transition", "COLLAB-001", "PENDING")
        self.assertEqual(self.task()["status"], "PENDING")

    def test_history_and_ledger_record_transitions(self):
        self.run_cli("accept", "COLLAB-001", "--actor", "openhands")
        self.run_cli("start", "COLLAB-001", "--actor", "openhands")
        history = self.task()["history"]
        self.assertEqual([h["to"] for h in history], ["PENDING", "ACCEPTED", "RUNNING"])
        lines = self.engine.LEDGER.read_text().strip().splitlines()
        self.assertEqual(len(lines), 3)
        last = json.loads(lines[-1])
        self.assertEqual(last["to"], "RUNNING")
        self.assertEqual(last["by"], "openhands")
        self.assertIn("at", last)


class TestEvidenceGate(SandboxedCase):
    def setUp(self):
        super().setUp()
        self.create()
        self.run_cli("accept", "COLLAB-001")
        self.run_cli("start", "COLLAB-001")
        self.run_cli("review", "COLLAB-001")

    def test_complete_without_evidence_is_refused(self):
        self.assertEqual(self.run_cli("complete", "COLLAB-001"), 2)
        self.assertEqual(self.task()["status"], "REVIEW_REQUIRED")

    def test_complete_without_files_is_refused(self):
        self.assertEqual(
            self.run_cli("complete", "COLLAB-001", "--evidence", "e=>r"), 2
        )
        self.assertEqual(self.task()["status"], "REVIEW_REQUIRED")

    def test_malformed_evidence_is_refused(self):
        self.assertEqual(
            self.run_cli("complete", "COLLAB-001", "--evidence", "no arrow"), 2
        )

    def test_complete_with_evidence_succeeds_and_persists_it(self):
        self.assertEqual(
            self.run_cli(
                "complete", "COLLAB-001", "--evidence", "pytest=>83 passed",
                "--files", "a.py", "--unresolved", "none",
            ),
            0,
        )
        doc = self.task()
        self.assertEqual(doc["evidence"][0]["command"], "pytest")
        self.assertEqual(doc["evidence"][0]["result"], "83 passed")
        self.assertEqual(doc["unresolved"], ["none"])


class TestApprovalGate(SandboxedCase):
    def test_destructive_task_needs_approval_before_accept(self):
        self.create("COLLAB-002", risk="destructive")
        self.assertEqual(self.run_cli("accept", "COLLAB-002"), 2)
        self.assertEqual(self.task("COLLAB-002")["status"], "PENDING")

    def test_scope_change_needs_approval(self):
        self.create("COLLAB-003", risk="scope-change")
        self.assertEqual(self.run_cli("accept", "COLLAB-003"), 2)

    def test_approval_unlocks_the_task(self):
        self.create("COLLAB-002", risk="destructive")
        self.assertEqual(
            self.run_cli("approve", "COLLAB-002", "--by", "owner", "--scope", "delete dist"),
            0,
        )
        self.assertEqual(self.run_cli("accept", "COLLAB-002"), 0)
        self.assertEqual(self.task("COLLAB-002")["approval"]["granted_by"], "owner")

    def test_read_only_task_needs_no_approval(self):
        self.create("COLLAB-004", risk="read-only")
        self.assertEqual(self.run_cli("accept", "COLLAB-004"), 0)

    def test_verify_flags_an_unapproved_advanced_task(self):
        self.create("COLLAB-002", risk="destructive")
        doc = self.task("COLLAB-002")
        doc["status"] = "RUNNING"          # bypass the CLI on purpose
        self.engine.save_task(doc)
        self.assertEqual(self.run_cli("verify"), 1)


class TestViewsAndRendering(SandboxedCase):
    def test_render_writes_the_three_views(self):
        self.create("COLLAB-001", title="first task")
        self.run_cli("render")
        for name in ("INBOX.md", "OUTBOX.md", "CURRENT_STATUS.md"):
            self.assertTrue((self.engine.COLLAB_DIR / name).exists(), name)

    def test_pending_task_appears_in_inbox_only(self):
        self.create("COLLAB-001", title="first task")
        self.run_cli("render")
        inbox = (self.engine.COLLAB_DIR / "INBOX.md").read_text()
        outbox = (self.engine.COLLAB_DIR / "OUTBOX.md").read_text()
        self.assertIn("COLLAB-001", inbox)
        self.assertNotIn("COLLAB-001", outbox)

    def test_completed_task_appears_in_outbox_with_evidence(self):
        self.create("COLLAB-001")
        self.run_cli("accept", "COLLAB-001")
        self.run_cli("start", "COLLAB-001")
        self.run_cli("review", "COLLAB-001")
        self.run_cli(
            "complete", "COLLAB-001", "--evidence", "pytest=>83 passed",
            "--files", "a.py",
        )
        self.run_cli("render")
        outbox = (self.engine.COLLAB_DIR / "OUTBOX.md").read_text()
        self.assertIn("COLLAB-001", outbox)
        self.assertIn("pytest", outbox)
        self.assertIn("83 passed", outbox)

    def test_status_view_counts_states(self):
        self.create("COLLAB-001")
        self.create("COLLAB-002")
        self.run_cli("accept", "COLLAB-002")
        self.run_cli("render")
        status = (self.engine.COLLAB_DIR / "CURRENT_STATUS.md").read_text()
        self.assertIn("| PENDING | 1 |", status)
        self.assertIn("| ACCEPTED | 1 |", status)

    def test_views_are_deterministic(self):
        self.create("COLLAB-001")
        self.run_cli("render")
        for name in ("INBOX.md", "OUTBOX.md", "CURRENT_STATUS.md"):
            path = self.engine.COLLAB_DIR / name
            first = path.read_text()
            self.run_cli("render")
            self.assertEqual(first, path.read_text(), name)

    def test_status_view_ignores_clock_and_environment(self):
        """CURRENT_STATUS must not depend on time, HEAD, branch or remote.

        The CI staleness check re-renders and diffs against the committed
        file, so any wall-clock or git-derived field would fail forever.
        """

        self.create("COLLAB-001")
        self.run_cli("render")
        path = self.engine.COLLAB_DIR / "CURRENT_STATUS.md"
        baseline = path.read_text()

        self.engine.utc_now = lambda: "2099-01-01T00:00:00Z"
        self.engine.current_commit = lambda: "f" * 40
        self.engine.current_branch = lambda: "some-other-branch"
        self.engine.git = lambda *a: "https://example.invalid/other.git"
        self.run_cli("render")

        self.assertEqual(baseline, path.read_text())

    def test_fingerprint_changes_only_with_state(self):
        self.create("COLLAB-001")
        a = self.engine.cmd_fingerprint.__wrapped__ if hasattr(
            self.engine.cmd_fingerprint, "__wrapped__"
        ) else None
        import io
        import contextlib

        def fp():
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                self.engine.main(["fingerprint"])
            return buf.getvalue().strip()

        before = fp()
        self.run_cli("accept", "COLLAB-001")
        after = fp()
        self.assertNotEqual(before, after)
        self.assertEqual(after, fp(), "stable when nothing changes")


class TestVerify(SandboxedCase):
    def test_verify_ok_on_a_clean_tree(self):
        self.create("COLLAB-001")
        self.assertEqual(self.run_cli("verify"), 0)

    def test_verify_detects_completed_without_evidence(self):
        self.create("COLLAB-001")
        doc = self.task()
        doc["status"] = "COMPLETED"
        self.engine.save_task(doc)
        self.assertEqual(self.run_cli("verify"), 1)

    def test_verify_detects_missing_report(self):
        self.create("COLLAB-001")
        doc = self.task()
        doc["status"] = "REVIEW_REQUIRED"
        doc["report"] = "COLLAB-001.md"
        self.engine.save_task(doc)
        self.assertEqual(self.run_cli("verify"), 1)

    def test_verify_passes_when_report_exists(self):
        self.create("COLLAB-001")
        (self.engine.REPORTS_DIR / "COLLAB-001.md").write_text("# report\n")
        # Advance through the CLI so the ledger stays consistent.
        self.run_cli("accept", "COLLAB-001")
        self.run_cli("start", "COLLAB-001")
        self.run_cli("review", "COLLAB-001")
        doc = self.task()
        doc["report"] = "COLLAB-001.md"
        self.engine.save_task(doc)
        self.assertEqual(self.run_cli("verify"), 0)

    def test_verify_detects_ledger_disagreement(self):
        self.create("COLLAB-001")
        self.run_cli("accept", "COLLAB-001")
        doc = self.task()
        doc["status"] = "PENDING"      # ledger still says ACCEPTED
        self.engine.save_task(doc)
        self.assertEqual(self.run_cli("verify"), 1)


class TestScanCommand(SandboxedCase):
    def test_scan_is_clean_on_generated_views(self):
        self.create("COLLAB-001")
        self.run_cli("render")
        self.assertEqual(self.run_cli("scan"), 0)

    def test_scan_flags_a_planted_secret(self):
        (self.engine.COLLAB_DIR / "leak.md").write_text("key: sk-" + "z" * 30)
        self.assertEqual(self.run_cli("scan"), 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
