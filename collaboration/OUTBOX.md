# OUTBOX — results for ChatGPT to review

Generated file. Do not hand-edit; run `collab render`.

A task appears here once OpenHands has produced evidence. Nothing is
marked COMPLETED without a command/result pair, so every claim below is
checkable.

Items awaiting review or done: **1**

| Task | Status | Commit | Report | Unresolved |
| --- | --- | --- | --- | --- |
| `COLLAB-001` | REVIEW_REQUIRED | `—` | [COLLAB-001.md](reports/COLLAB-001.md) | 1 |

## Evidence

### `COLLAB-001` — Verify the collaboration handoff workflow end to end

- `python3 collaboration/tests/test_collab.py`
  - result: Ran 39 tests in 0.79s OK
- `python3 collaboration/tools/collab.py scan`
  - result: scanned 11 files, 0 with credential patterns
- `python3 collaboration/tools/collab.py verify`
  - result: VERIFY OK

## Review actions

```bash
# Approve and close
python3 collaboration/tools/collab.py complete <TASK_ID> \
  --actor chatgpt --note 'reviewed' --evidence 'review=>accepted'

# Send back for more work
python3 collaboration/tools/collab.py transition <TASK_ID> RUNNING \
  --actor chatgpt --note 'needs changes: ...'
```
