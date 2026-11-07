# PROTOCOL — free GitHub handoff between ChatGPT and OpenHands

This document is the contract. It exists so that two parties with no shared
memory — ChatGPT and an OpenHands agent — can hand work back and forth through
one public GitHub repository, at zero cost, with no manual copy-paste of long
reports.

Everything here is implemented in `collaboration/tools/collab.py` (Python
standard library only, no network, no secrets). Nothing in this protocol
requires ChatGPT Pro, a paid API, or a background worker.

---

## 1. What was verified about the two platforms

These are findings from the live environment, not assumptions.

| Question | Answer | How it was verified |
| --- | --- | --- |
| Does OpenHands Cloud have a GitHub App on this account? | Yes, `openhands-ai`, installation id `179374977`, repository access `all` | `GET /api/v1/git/installations/search?provider=github` and `GET /api/v1/user/installations` |
| Is this repository in scope for that App? | Yes, it appears in the App's repository list | `GET /api/v1/git/repositories/search?provider=github&installation_id=179374977` |
| Does OpenHands Cloud trigger automatically on GitHub events? | **Yes.** Labelling an issue `openhands`, or commenting on it with a message starting `@openhands`, makes OpenHands start working. `@openhands` in a pull-request comment also works. | Official docs, GitHub Integration page (quoted in `PROJECT_CONTEXT.md`) |
| Does it need a self-hosted poller or always-on worker? | **No.** The trigger is delivered by GitHub to OpenHands Cloud. Nothing has to run on our side. | Same source; there is no cron/polling API in the OpenHands OpenAPI spec |
| Are GitHub Actions available as a free runner? | Unknown — not verifiable here. The GitHub App token lacks `actions: read` permission (`403 Resource not accessible by integration`). The protocol therefore does **not** depend on Actions. | `GET /repos/.../actions/permissions` returned 403 |
| Can ChatGPT read and write repository files directly? | Depends on the ChatGPT surface. GitHub's own ChatGPT app and Custom GPT actions with a fine-grained PAT can. Plain chat without a connector cannot write. Section 7 gives the exact fallback. | Documented limitation, see section 7 |

Conclusion: the free, reliable design is **GitHub issues as the trigger
channel** and **files in `collaboration/` as the state channel**. Both are free
on public repositories, and the trigger side is event-driven, so nothing has to
poll.

---

## 2. The two channels

```
                       ┌─────────────────────────────────────────┐
   ChatGPT  ──write──▶  │  GitHub issues                          │  ──event──▶ OpenHands
   (or human)           │  label "openhands" / comment "@openhands"│            Cloud
                       └─────────────────────────────────────────┘
                                        │
                                        ▼
                       ┌─────────────────────────────────────────┐
                       │  collaboration/  (this repository)       │
                       │    tasks/*.json   ← source of truth      │
                       │    INBOX.md       ← generated view       │
                       │    OUTBOX.md      ← generated view       │
                       │    CURRENT_STATUS.md ← generated view    │
                       │    ledger.jsonl   ← append-only audit    │
                       │    reports/*.md   ← human-readable detail│
                       └─────────────────────────────────────────┘
                                        │
   ChatGPT  ◀──read───  raw.githubusercontent.com (public, no auth)
```

* **Trigger channel = issues.** An issue is where a task is announced and where
  the `@openhands` mention happens. It is also where OpenHands comments back
  with a link to its run.
* **State channel = files.** Files are reviewable, diffable, offline-readable
  and cacheable. `raw.githubusercontent.com` serves them to any client that can
  fetch a URL, with no token, so ChatGPT does not need a custom MCP connector to
  read state.

---

## 3. Task identity and lifecycle

**Task id format:** `AREA-NNN`, uppercase, for example `COLLAB-001`,
`BRIDGE-014`. The id is validated against `^[A-Z][A-Z0-9]*-\d{3,}$`.

**Why the id matters:** the task file is named `<TASK_ID>.json`. A duplicate id
is therefore a file collision, and `collab create` refuses it. `collab verify`
independently re-checks for duplicates. This is the duplicate-execution guard at
the storage layer; the state machine is the guard at the logic layer.

**States and legal transitions:**

```
  PENDING ──▶ ACCEPTED ──▶ RUNNING ──▶ REVIEW_REQUIRED ──▶ COMPLETED
     ▲            │                        │
     │            ▼                        ▼
     └──────── BLOCKED ◀───────────────────┘
```

| From | May go to |
| --- | --- |
| PENDING | ACCEPTED, BLOCKED |
| ACCEPTED | RUNNING, BLOCKED, PENDING |
| RUNNING | REVIEW_REQUIRED, BLOCKED |
| REVIEW_REQUIRED | COMPLETED, RUNNING, BLOCKED |
| COMPLETED | _terminal_ |
| BLOCKED | PENDING, ACCEPTED |

Every transition appends a line to `ledger.jsonl` and a `history` entry to the
task file, each with an ISO-8601 UTC timestamp, the actor and the branch.

**Idempotency.** Re-applying the state a task is already in is refused with
"already <STATE>". A second `accept` on a claimed task is refused. Two agents
therefore cannot both start the same task.

---

## 4. Gates

### 4.1 Evidence gate

`COMPLETED` cannot be reached without:

1. at least one evidence entry of the form `command => result`, and
2. a non-empty `files_changed` list.

The tool refuses the transition otherwise. This is why the protocol can say
"never claim a task is completed without evidence" and mean it — it is enforced
in code, and `collab verify` re-checks it later, including in a fresh clone.

### 4.2 Approval gate

Tasks are classified `read-only`, `write`, `destructive` or `scope-change`.

Tasks whose risk is `destructive` or `scope-change` carry an `approval` block.
`collab accept`, `collab start` and any later advance refuse to run while
`approval.granted_by` is empty. Approval is recorded with:

```bash
python3 collaboration/tools/collab.py approve COLLAB-002 \
  --by "Gautier7799" --scope "delete stale build artifacts in mcp-bridge/dist"
```

The approval names a human and a scope. It is logged in the ledger and visible
in the task file, so there is a durable record of who authorised what.

Merging into `main` is treated as `destructive` and therefore always needs this
gate. Nothing in this repository auto-merges.

### 4.3 Secret gate

Every document written by the engine passes through `scan_secrets` first.
Patterns include OpenHands keys, `sk-` keys, GitHub tokens (`ghp_`, `github_pat_`),
Slack tokens, AWS keys, Google API keys, PEM private-key headers, JWTs, literal
`Bearer <token>` strings and `key = value` assignments where the value looks
like a credential. Field names such as `api_key`, `token`, `password` are
rejected outright.

If a match is found the write is refused and the matched value is **never**
echoed — only the pattern name is reported, so a refused write cannot itself
leak the secret into a log or a chat transcript.

`collab scan` runs the same patterns over the whole `collaboration/` tree (or the
whole repository with `--all`) and exits non-zero on a hit, so it can gate a
commit.

---

## 5. Who does what

| Step | Actor | Command |
| --- | --- | --- |
| 1. Announce a task | ChatGPT or human | open an issue, then `collab create` |
| 2. Trigger OpenHands | ChatGPT or human | add label `openhands`, or comment `@openhands` |
| 3. Claim | OpenHands | `collab accept <ID> --actor openhands` |
| 4. Work | OpenHands | `collab start <ID> --actor openhands` |
| 5. Publish result | OpenHands | `collab review <ID> --commit <sha> --files ... --evidence 'cmd=>result'` |
| 6. Review | ChatGPT | read `OUTBOX.md` and `reports/<ID>.md` |
| 7. Close or bounce | ChatGPT | `collab complete <ID> --actor chatgpt --evidence 'review=>accepted'` or `collab transition <ID> RUNNING --actor chatgpt --note '...'` |

OpenHands must run step 5 after **every** milestone, not only at the end, so the
state file is always an honest snapshot.

---

## 6. Report contract

Each task that reaches review gets `collaboration/reports/<TASK_ID>.md`
containing, in order:

1. Task id, title, status, branch, commit sha.
2. What changed — the `files_changed` list.
3. Evidence — each `command => result` pair, verbatim.
4. Test results — the `tests` list.
5. Build artifacts — the `artifacts` list, with sizes and hashes where relevant.
6. Unresolved issues — the `unresolved` list. An empty list is written as
   "none", never omitted.

The template is `collaboration/reports/TEMPLATE.md`.

---

## 7. ChatGPT compatibility

### 7.1 Reading (works everywhere, no connector, no token)

`raw.githubusercontent.com` serves public repository files over HTTPS with no
authentication. Any ChatGPT session that can fetch a URL — browsing, a link, or
a pasted URL — can read:

```
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/INBOX.md
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/OUTBOX.md
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/CURRENT_STATUS.md
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/PROJECT_CONTEXT.md
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/PROTOCOL.md
```

Per-task files and reports follow the same shape:

```
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/tasks/COLLAB-001.json
https://raw.githubusercontent.com/Gautier7799/NothingOS5-Lawnchair-16.7/collaboration-hub/collaboration/reports/COLLAB-001.md
```

`collab fingerprint` prints a sha256 over all task states. A client can fetch it
first and skip the rest of the reads when it is unchanged — cheap polling
without any server.

### 7.2 Writing

Writing is the part that is **not** universally available in plain chat, and it
is stated here plainly rather than glossed over:

| Capability | Available in plain ChatGPT chat? | Fallback |
| --- | --- | --- |
| Read protocol files | Yes | — |
| Open a GitHub issue | Yes, if the GitHub app/integration is connected to the account | Human opens the issue; or a fine-grained PAT via a Custom GPT action |
| Apply the `openhands` label | Yes, via the same integration | Human clicks the label (one click) |
| Edit `INBOX.md` / task files | No, not without a GitHub-connected app | **Comment on the issue instead.** OpenHands is the only writer of the state files. |
| Comment `@openhands` | Yes, same integration | Human posts the comment |

**The design decision that removes the blocker:** ChatGPT does not need write
access to the state files at all. It writes to the *issue* — a native GitHub
comment, which is the one write ChatGPT reliably has — and OpenHands mirrors the
issue into `tasks/*.json`. One writer per file, no merge conflicts, and no
requirement for a paid connector.

If ChatGPT has no GitHub write access at all in a given session, the minimal
fallback is: ChatGPT produces the task text, the human pastes it into a new
issue once, and everything downstream is automatic. That is a single manual
step per task, not per message.

### 7.3 Why not a custom MCP connector

A custom MCP connector would need a deployed server and, on some plans, a paid
tier. The point of this phase is a workflow that costs nothing and works today,
so the protocol deliberately uses plain HTTPS file reads plus native GitHub
issues. The existing read-only MCP bridge remains available and unchanged for
anyone who wants richer conversation introspection.

---

## 8. Consistency and conflict rules

* One writer per file. Only the engine writes `INBOX.md`, `OUTBOX.md`,
  `CURRENT_STATUS.md`, `tasks/*.json` and `ledger.jsonl`.
* Views are always regenerated from task files; they are never merged by hand.
* `ledger.jsonl` is append-only. `collab verify` cross-checks the ledger's last
  recorded state against each task file and fails on disagreement.
* The engine writes to whatever branch is checked out. Both parties agree the
  working branch is `collaboration-hub`; `main` is out of scope.
* Timestamps are UTC, ISO-8601, second precision.

---

## 9. Full command reference

```bash
# Task lifecycle
python3 collaboration/tools/collab.py create COLLAB-001 --title "..." \
    --owner chatgpt --risk read-only --objective "..." \
    --acceptance "criterion one" --acceptance "criterion two"
python3 collaboration/tools/collab.py approve COLLAB-002 --by "<human>" --scope "..."
python3 collaboration/tools/collab.py accept   COLLAB-001 --actor openhands
python3 collaboration/tools/collab.py start    COLLAB-001 --actor openhands
python3 collaboration/tools/collab.py review   COLLAB-001 --actor openhands \
    --commit <sha> --files a.py,b.py --evidence "pytest=>83 passed" \
    --tests "pytest: 83 passed" --unresolved "none"
python3 collaboration/tools/collab.py complete COLLAB-001 --actor chatgpt \
    --evidence "review=>accepted"
python3 collaboration/tools/collab.py block    COLLAB-001 --actor openhands \
    --note "needs a decision"

# Inspection
python3 collaboration/tools/collab.py list --status PENDING REVIEW_REQUIRED
python3 collaboration/tools/collab.py show COLLAB-001
python3 collaboration/tools/collab.py fingerprint

# Maintenance
python3 collaboration/tools/collab.py render
python3 collaboration/tools/collab.py scan          # collaboration/ tree
python3 collaboration/tools/collab.py scan --all    # whole repository
python3 collaboration/tools/collab.py verify
```

Every command is offline and deterministic. `collab.py --help` lists them.
