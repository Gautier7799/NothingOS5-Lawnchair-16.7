# COLLAB-001 — Verify the collaboration handoff workflow end to end

| Field | Value |
| --- | --- |
| Status | REVIEW_REQUIRED |
| Owner | openhands |
| Risk | read-only |
| Branch | `collaboration-hub` |
| Commit | `<filled by the committing session>` |
| Updated | `<ISO-8601 UTC>` |
| Issue | none (demonstration task) |

## Objective

Demonstrate that a task can be published, claimed, executed, reviewed and closed
using only the collaboration protocol, with no destructive effects and no
changes to `main` or to the read-only MCP bridge.

## What changed

| File | Change |
| --- | --- |
| `collaboration/tools/collab.py` | added — protocol engine |
| `collaboration/tests/test_collab.py` | added — 39 engine tests |
| `collaboration/PROTOCOL.md` | added — the contract |
| `collaboration/README.md` | added — entry point |
| `collaboration/PROJECT_CONTEXT.md` | added — durable memory |
| `collaboration/INBOX.md`, `OUTBOX.md`, `CURRENT_STATUS.md` | generated views |
| `collaboration/tasks/COLLAB-001.json` | created by this task |

## Evidence

```
$ python3 collaboration/tests/test_collab.py
Ran 39 tests in 0.79s
OK
```

```
$ python3 collaboration/tools/collab.py scan
scanned 11 files, 0 with credential patterns
```

```
$ python3 collaboration/tools/collab.py verify
VERIFY OK
```

```
$ python3 collaboration/tools/collab.py create COLLAB-001 --title "duplicate attempt"
error: Task COLLAB-001 already exists with status PENDING.
       Duplicate ids are refused so a task can never be executed twice.
exit=2
```

```
$ python3 collaboration/tools/collab.py transition COLLAB-001 PENDING
error: Illegal transition RUNNING -> PENDING for COLLAB-001.
       Allowed from RUNNING: ['BLOCKED', 'REVIEW_REQUIRED'].
exit=2
```

```
$ python3 collaboration/tools/collab.py complete COLLAB-001 --actor chatgpt
error: Refusing to mark COLLAB-001 COMPLETED without evidence.
exit=2
```

## Test results

| Suite | Command | Result |
| --- | --- | --- |
| collaboration protocol | `python3 collaboration/tests/test_collab.py` | 39 passed, 0 failed |
| mcp bridge (regression) | `PYTHONPATH=src .venv/bin/python -m pytest` in `mcp-bridge/` | see `mcp-bridge/docs/VERIFICATION.md` |

## Build artifacts

| Artifact | Size | SHA-256 |
| --- | --- | --- |
| `collaboration/tools/collab.py` | see archive manifest | recorded in the archive manifest |

## Unresolved issues

- none

## Notes for the reviewer

This is a read-only demonstration. It created one task file and regenerated the
three views. It did not touch `main`, did not modify `mcp-bridge/`, and did not
issue any write to OpenHands Cloud. The duplicate-id, illegal-transition and
missing-evidence refusals above are the point of the task: each one is a guard
being exercised, not a failure.
