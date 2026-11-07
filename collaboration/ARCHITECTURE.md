# ARCHITECTURE — GitHub collaboration and handoff workflow

This document describes how ChatGPT and OpenHands exchange work through this
repository at zero cost, and why it is built this way. It is the architectural
companion to [`PROTOCOL.md`](PROTOCOL.md) (the contract) and
[`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) (the durable memory).

---

## 1. Problem

Two parties must collaborate on this repository:

* **ChatGPT** — no filesystem, no shell, no persistent memory between chats, and
  in plain chat no direct repository write access.
* **OpenHands** — has a shell and a filesystem, but each conversation starts
  with no memory of the previous one.

The naive solution is copy-pasting long reports between them. That is slow,
error-prone, loses state, and cannot be reviewed or diffed. The objective was a
workflow that is free, durable, reviewable and automatic where possible.

---

## 2. Constraints discovered by investigation

Everything in this table was checked against the live environment. The design
follows from these facts.

| Fact | Evidence | Consequence |
| --- | --- | --- |
| OpenHands Cloud has the `openhands-ai` GitHub App installed with access to this repo | `GET /api/v1/git/installations/search?provider=github` → `["179374977"]`; repo appears in `GET /api/v1/git/repositories/search` | GitHub issues can trigger OpenHands |
| OpenHands starts work when an issue is labelled `openhands` or a comment starts `@openhands` | Official GitHub Integration documentation | The trigger channel is native and free; no custom integration needed |
| The trigger is event-driven, delivered by GitHub | Same documentation; no cron/polling endpoint exists in the OpenHands OpenAPI spec (242 paths searched) | No background worker, no always-on process |
| GitHub Actions availability could not be verified | `GET /repos/.../actions/permissions` → `403 Resource not accessible by integration` (App token lacks `actions`) | The protocol must not depend on Actions; CI is optional |
| `POST /integration/github/events` needs an `x-hub-signature-256` HMAC only GitHub and OpenHands can produce | OpenAPI spec | We must not call it; only GitHub does |
| `POST /api/v1/webhooks/conversations` is an inbound callback *from* OpenHands | OpenAPI spec | Not a subscription mechanism for us |
| `raw.githubusercontent.com` serves public files with no auth | Standard GitHub behaviour, verified by fetching this branch's raw files | ChatGPT can read state with no token and no MCP connector |
| A repository label named `openhands` does not exist yet | `GET /repos/.../labels` returns 10 unrelated labels | It must be created once; the `@openhands` comment trigger needs nothing |

---

## 3. Design

### 3.1 Two channels, one writer each

```
   ChatGPT ──writes──▶ ISSUES  ──GitHub event──▶ OpenHands Cloud
                        │
                        │ (OpenHands mirrors the issue into a task file)
                        ▼
                    collaboration/  ◀── only the engine writes here
                        │
   ChatGPT ◀──reads──── raw.githubusercontent.com (public HTTPS, no auth)
```

**Trigger channel: GitHub issues.** An issue is where a task is announced and
where the `@openhands` mention or `openhands` label fires the event. OpenHands
comments back on the same issue with a link to its run.

**State channel: files under `collaboration/`.** Files are diffable, reviewable,
cacheable, readable offline, and — crucially — readable by any client that can
fetch a URL. That last property is what removes the need for a custom MCP
connector.

The single most important design decision: **ChatGPT does not need write access
to the state files.** It writes to the issue (the one write it reliably has) and
OpenHands mirrors the issue into a task file. One writer per file means no merge
conflicts and no coordination protocol between two writers.

### 3.2 Task files are the source of truth; the Markdown views are derived

`tasks/<TASK_ID>.json` is authoritative. `INBOX.md`, `OUTBOX.md` and
`CURRENT_STATUS.md` are generated from it by `collab render`. This avoids the
classic failure of hand-maintained status documents, which drift from reality
the moment two people edit them.

Because the file name is the task id, a duplicate id is a filesystem collision —
the cheapest possible duplicate guard, checked again by `collab verify`.

### 3.3 The ledger

`ledger.jsonl` is append-only. Every transition writes one line: timestamp, task
id, from-state, to-state, actor, branch, commit. `collab verify` cross-checks
the ledger's last recorded state against each task file and fails on
disagreement. The ledger is the audit trail that neither party can quietly
rewrite, because rewriting it is visible in the diff.

### 3.4 Gates, not conventions

Three rules could easily have been documentation-only. They are instead enforced
in code, because a convention that is not enforced is not a rule:

| Rule | Enforcement point |
| --- | --- |
| Nothing is `COMPLETED` without evidence | `apply_transition` refuses; `verify` re-checks in a fresh clone |
| Destructive / scope-changing work needs human approval | `apply_transition` refuses to advance a task with an empty `approval.granted_by` |
| No secret is ever written | `_check_document` runs `scan_secrets` on every persisted document; forbidden field names are rejected outright |

The secret scanner never echoes the matched value — only the pattern name — so a
refused write cannot itself leak the secret into a log, a chat transcript, or a
tool result.

### 3.5 State machine

```
  PENDING ──▶ ACCEPTED ──▶ RUNNING ──▶ REVIEW_REQUIRED ──▶ COMPLETED
     ▲            │                        │
     │            ▼                        ▼
     └──────── BLOCKED ◀───────────────────┘
```

`COMPLETED` is terminal. Every other state can reach `BLOCKED`, and `BLOCKED`
can return to `PENDING` or `ACCEPTED`, so a stalled task is never a dead end.
Re-applying the current state is refused, which is the logic-layer duplicate
guard.

---

## 4. Components

| Component | Path | Responsibility |
| --- | --- | --- |
| Protocol engine | `tools/collab.py` | State machine, gates, rendering, ledger, scanning. Standard library only. |
| Task store | `tasks/<ID>.json` | Authoritative per-task state, including evidence and history |
| Generated views | `INBOX.md`, `OUTBOX.md`, `CURRENT_STATUS.md` | Human- and model-readable summaries |
| Reports | `reports/<ID>.md` | Narrative detail behind a result |
| Ledger | `ledger.jsonl` | Append-only transition audit |
| Context | `PROJECT_CONTEXT.md` | Durable memory so no session starts cold |
| Contract | `PROTOCOL.md` | The rules and the compatibility matrix |
| Engine tests | `tests/test_collab.py` | 39 tests, offline, isolated in temp dirs |
| Optional CI | `.github/workflows/collaboration-verify.yml` | Re-runs tests, verify, scan and view-freshness |
| Issue template | `.github/ISSUE_TEMPLATE/openhands-task.yml` | Makes publishing a well-formed task a form |

The engine has no dependencies outside the Python standard library and performs
no network I/O. It can therefore run anywhere Python 3 runs — including inside
an OpenHands conversation with no setup.

---

## 5. Interaction sequences

### 5.1 Publish and run

```
ChatGPT / human          GitHub                  OpenHands Cloud        Engine
     │                     │                            │                 │
     │ open issue          │                            │                 │
     ├────────────────────▶│                            │                 │
     │ label `openhands`   │                            │                 │
     ├────────────────────▶│  webhook event             │                 │
     │                     ├───────────────────────────▶│                 │
     │                     │                            │ starts a run    │
     │                     │◀── comment: "on it" + link ─┤                 │
     │                     │                            │ accept/start    │
     │                     │                            ├────────────────▶│
     │                     │                            │  work…          │
     │                     │                            │ review + evidence
     │                     │                            ├────────────────▶│
     │                     │◀── pull request ────────────┤                 │
     │                     │                            │                 │
     │ read OUTBOX.md      │                            │                 │
     ├─────────────────────┴────────────────────────────┴─────────────────┤
     │  (plain HTTPS, no token)                                            │
     │                                                                     │
     │ complete  ─────────────────────────────────────────────────────────▶│
```

### 5.2 Review loop

ChatGPT reads `OUTBOX.md` → `tasks/<ID>.json` → `reports/<ID>.md`. If satisfied,
it records `COMPLETED` with its own evidence line. If not, it transitions the
task back to `RUNNING` with a note. Both actions append to the ledger, so the
bounce is as visible as the completion.

---

## 6. Why not alternatives

| Alternative | Rejected because |
| --- | --- |
| Copy-paste reports into chat | Loses state, not reviewable, not diffable, no audit trail |
| A custom MCP connector as the transport | Needs a deployed server; some plans require a paid tier. The objective was a free workflow. The existing read-only bridge remains available for conversation introspection. |
| GitHub Actions as the trigger | Availability unverified here (403 on the permissions endpoint). Making it a dependency would risk a non-functioning workflow. It is included as an optional extra. |
| A background poller | OpenHands Cloud has no cron/scheduler API, and running a persistent process is not free. The native event trigger makes polling unnecessary. |
| Storing state in issue comments only | Hard to diff, hard to machine-read, and issue threads interleave discussion with state. Files are better for state; issues are better for discussion and triggering. |

---

## 7. Security properties

* **No secrets in the repository.** Enforced on every write, and re-checked by
  `collab scan`, which exits non-zero on a hit.
* **No new write or execute capability in the MCP bridge.** The bridge is
  untouched by this work; `git status mcp-bridge/` is clean, and its 83 tests
  pass unchanged.
* **Least privilege.** The protocol needs only public read access on the
  ChatGPT side, and no credentials at all for the engine.
* **Human gate on anything dangerous.** `destructive` and `scope-change` tasks
  cannot advance without a named approver. Merging to `main` is `destructive`.
* **No automatic merge.** Nothing in this repository merges anything.
* **Auditability.** The ledger plus per-task history means every state change
  has a who, a when and a why.

---

## 8. Known limitations

1. **Creating the issue may need a human click** if ChatGPT has no GitHub write
   access in a given session. Everything downstream is automatic. This is the
   one manual step, and it is per task, not per message.
2. **GitHub Actions could not be verified** from the App token used here. The CI
   workflow is optional and untested in this environment.
3. **The engine is not an executor.** It records and gates work; it does not
   perform it. Execution happens in the OpenHands Cloud conversation.
4. **The build sandbox is ephemeral.** The repository is the durable artefact.
5. **One repository, one working branch.** Multi-branch or multi-repository
   coordination is out of scope for this phase.

---

## 9. Extension points

* A `--reporter` flag on `collab.py` could emit GitHub check runs if Actions
  become available.
* `collab fingerprint` already gives a cheap change-detection primitive for any
  future client that wants to poll without refetching everything.
* Additional risk classes can be added to `RISK_CLASSES` and
  `APPROVAL_REQUIRED` without touching the state machine.
