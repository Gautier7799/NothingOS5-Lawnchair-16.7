# INBOX — tasks for OpenHands

Generated file. Do not hand-edit; run `collab render`.

How a task gets here: ChatGPT (or a human) creates the task, then
triggers OpenHands on GitHub by adding the `openhands` label to the
tracking issue or commenting `@openhands` on it. OpenHands then claims
the task with `collab accept`, which moves it to ACCEPTED.

Open tasks: **0**

| Task | Status | Risk | Title | Issue | Updated |
| --- | --- | --- | --- | --- | --- |
| — | — | — | _No open tasks_ | — | — |

## Claim protocol

```bash
python3 collaboration/tools/collab.py list --status PENDING
python3 collaboration/tools/collab.py accept <TASK_ID> --actor openhands
python3 collaboration/tools/collab.py start  <TASK_ID> --actor openhands
```

Claiming is idempotent-safe: the first `accept` wins, and a second
`accept` on the same task is refused, so two agents cannot run the same
task twice.
