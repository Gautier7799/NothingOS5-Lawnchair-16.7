# collaboration — free GitHub handoff between ChatGPT and OpenHands

A zero-cost workflow that lets ChatGPT and an OpenHands agent hand work back and
forth through this repository, with no paid services, no background worker, and
no manual copying of long reports.

**Start here:** [`PROTOCOL.md`](PROTOCOL.md) is the contract.
[`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) is the durable memory.

---

## How it works in one picture

```
ChatGPT (or human)                      OpenHands Cloud
      │                                        │
      │ 1. opens an issue describing a task     │
      │ 2. labels it `openhands`                │
      │    or comments `@openhands`             │
      └───────────────► GitHub ────────────────►│ 3. starts working automatically
                                               │
                                               │ 4. runs collaboration/tools/collab.py
                                               │    to claim, run and publish
                                               ▼
                              collaboration/tasks/*.json  ← source of truth
                              collaboration/INBOX.md      ← generated
                              collaboration/OUTBOX.md     ← generated
                              collaboration/CURRENT_STATUS.md ← generated
                              collaboration/ledger.jsonl  ← append-only audit
                              collaboration/reports/*.md  ← detail
                                               │
      ┌────────────────────────────────────────┘
      │ 5. ChatGPT reads the raw files over HTTPS (no token needed)
      ▼
   review, then close or send back
```

The trigger is **event-driven** — GitHub delivers it to OpenHands Cloud. Nothing
has to poll. Reading is plain HTTPS, so ChatGPT needs no custom MCP connector.

---

## Layout

| Path | What it is | Who writes it |
| --- | --- | --- |
| `INBOX.md` | Open tasks for OpenHands | engine only |
| `OUTBOX.md` | Results awaiting review | engine only |
| `CURRENT_STATUS.md` | Whole board and latest task update | engine only |
| `PROJECT_CONTEXT.md` | Durable project memory | humans / agents, by hand |
| `PROTOCOL.md` | The contract | by hand |
| `tasks/<ID>.json` | Per-task source of truth | engine only |
| `reports/<ID>.md` | Human-readable result detail | OpenHands, from `reports/TEMPLATE.md` |
| `ledger.jsonl` | Append-only transition log | engine only |
| `tools/collab.py` | The engine (stdlib only) | by hand |
| `tests/test_collab.py` | 39 tests for the engine | by hand |

Views are generated. Editing them by hand will be overwritten by the next
`collab render`, and the optional CI job fails if they are stale.

---

## Quick start

```bash
# see the board
python3 collaboration/tools/collab.py list

# run the engine's own tests
python3 collaboration/tests/test_collab.py

# check integrity (evidence, approvals, ledger agreement)
python3 collaboration/tools/collab.py verify

# scan for credentials
python3 collaboration/tools/collab.py scan
```

## OpenHands: pick up and finish a task

```bash
python3 collaboration/tools/collab.py list --status PENDING
python3 collaboration/tools/collab.py accept COLLAB-001 --actor openhands
python3 collaboration/tools/collab.py start  COLLAB-001 --actor openhands
# ... do the work ...
python3 collaboration/tools/collab.py review COLLAB-001 --actor openhands \
  --commit "$(git rev-parse HEAD)" \
  --files "path/a.py,path/b.py" \
  --evidence "python3 collaboration/tests/test_collab.py=>39 tests OK" \
  --tests "collab protocol: 39 passed" \
  --unresolved "none"
```

Then write `reports/COLLAB-001.md` from the template and commit.

## ChatGPT: publish a task

Open an issue from the **OpenHands task** template (or paste its content), then
add the `openhands` label or comment `@openhands`. No repository write access to
the state files is needed — see `PROTOCOL.md` §7.2 for why that matters and what
the fallback is.

## ChatGPT: review a result

Read, in this order:

1. `OUTBOX.md` — what is ready
2. `CURRENT_STATUS.md` — the whole board
3. `tasks/<ID>.json` — machine-readable detail, including evidence
4. `reports/<ID>.md` — the write-up

Then close it or bounce it:

```bash
python3 collaboration/tools/collab.py complete COLLAB-001 --actor chatgpt \
  --evidence "review=>accepted" --files none
python3 collaboration/tools/collab.py transition COLLAB-001 RUNNING \
  --actor chatgpt --note "needs changes: ..."
```

---

## The three guarantees, and how they are enforced

| Guarantee | Enforcement |
| --- | --- |
| No task is ever claimed twice | Task file name is the task id; `create` refuses duplicates; re-applying a state is refused; `verify` re-checks |
| Nothing is "completed" without proof | `COMPLETED` requires at least one `command => result` pair **and** a changed-file list; `verify` re-checks in a fresh clone |
| No secret is ever committed | Every write passes `scan_secrets`; `key`/`token`/`password` field names are rejected; the matched value is never echoed; `scan` gates the tree |

Plus the approval gate: `destructive` and `scope-change` tasks cannot advance
until a human is named in an `approval` block. Merging to `main` is classed
`destructive`, so it always needs that gate. Nothing auto-merges.

---

## What is not automated, and why

Stated plainly, because a workflow you cannot trust is worse than none:

* **Creating the issue** may need a human click if ChatGPT has no GitHub write
  access in your session. Everything after that is automatic.
* **GitHub Actions availability could not be verified** from the App token used
  here (it returns `403` for the Actions permissions endpoint). The CI workflow
  is therefore optional. The protocol works without it.
* **OpenHands Cloud runs the task in its own cloud**, not in this sandbox. The
  engine here is the bookkeeping and the contract; it does not itself execute
  arbitrary work.
* The sandbox in which this was built is ephemeral. The repository is the
  durable artefact.

See `PROTOCOL.md` §7 for the full ChatGPT compatibility discussion.
