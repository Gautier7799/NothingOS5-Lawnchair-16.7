# PROJECT_CONTEXT — persistent memory across sessions

This file is the durable memory of the project. Any new session — ChatGPT or
OpenHands — should be able to read this file plus `CURRENT_STATUS.md` and resume
work without asking the user to repeat anything.

Last updated: 2026-11-07

---

## 1. What this project is

`NothingOS5-Lawnchair-16.7` is a Launcher (Nothing OS 5 / Lawnchair 16.7)
codebase. It is the upstream project, and it is **not** to be modified except
where a task explicitly says so.

On top of it, two things have been built:

1. **`mcp-bridge/`** — a production-ready, read-only MCP server that exposes the
   OpenHands Cloud REST API to ChatGPT. Complete, tested, deployed to a sandbox
   host for verification. It must not be weakened: it stays read-only, and it
   must keep passing its 83 tests.
2. **`collaboration/`** — this directory. The free GitHub handoff protocol
   between ChatGPT and OpenHands. No paid services, no background workers.

---

## 2. Repository and branch map

| Branch | Commit | Meaning |
| --- | --- | --- |
| `main` | `6affb708e829d07e2ff06d0f5e19a11c2faa8a43` | Upstream `Initial commit`. **Frozen — never modify.** |
| `mcp-bridge` | `e47512a2be8a1db4b1a0669d45bb786854528706` | The MCP bridge plus its docs and the earlier handoff report. |
| `collaboration-hub` | _tip moves_ | This work: the collaboration protocol. Branched from `mcp-bridge`. |

Working rule: all new work happens on `collaboration-hub`. Nothing merges to
`main` without the approval gate in `PROTOCOL.md` §4.2.

Repository: https://github.com/Gautier7799/NothingOS5-Lawnchair-16.7

---

## 3. The MCP bridge, in one paragraph

`mcp-bridge/` is an ASGI app (FastMCP + Streamable HTTP) exposing five read-only
tools: `openhands_list_conversations`, `openhands_get_conversation`,
`openhands_get_conversation_status`, `openhands_get_conversation_events`,
`openhands_download_trajectory`. It authenticates callers with a bearer token or
OAuth 2.1, authenticates to OpenHands Cloud with `OPENHANDS_CLOUD_API_KEY` read
from the environment or a `*_FILE` mount, and only ever issues HTTP `GET`
upstream. Full detail: `mcp-bridge/docs/ARCHITECTURE.md` and
`mcp-bridge/docs/SECURITY.md`.

It is unrelated to the collaboration protocol except that both live in this
repository and both must keep their tests green.

---

## 4. Platform findings (verified, not assumed)

### 4.1 OpenHands Cloud can be triggered from GitHub — natively

The OpenHands Cloud GitHub App (`openhands-ai`, installation `179374977`) is
installed and has access to this repository. According to the official GitHub
Integration documentation:

> To allow OpenHands to work directly from GitHub directly, you must give
> OpenHands access to your repository. Once access is given, you can use
> OpenHands by labeling the issue or by tagging `@openhands`.
>
> **Working with Issues** — On your repository, label an issue with `openhands`
> or add a message starting with `@openhands`. OpenHands will:
> - Comment on the issue to let you know it is working on it. You can click on
>   the link to track the progress on OpenHands Cloud.
> - Open a pull request if it determines that the issue has been successfully
>   resolved.
> - Comment on the issue with a summary of the performed tasks and a link to the
>   PR.
>
> **Working with Pull Requests** — To get OpenHands to work on pull requests,
> mention `@openhands` in the comments.

Consequences for this design:

* The trigger is **event-driven**. No poller, no cron, no always-on worker is
  needed on our side. GitHub delivers the event to OpenHands Cloud.
* The trigger is **free** and works on a public repository.
* A repository label named `openhands` does not exist yet in this repository
  (existing labels: `accessibility`, `bug`, `documentation`, `duplicate`,
  `enhancement`, `good first issue`, `help wanted`, `invalid`, `question`,
  `wontfix`). It must be created once before the label trigger can be used; the
  `@openhands` comment trigger works without it.
* OpenHands opens its own pull request when it finishes. It does not merge.

### 4.2 What could not be verified

* **GitHub Actions availability.** The GitHub App token has no `actions`
  permission, so `GET /repos/{owner}/{repo}/actions/permissions` returns
  `403 Resource not accessible by integration`. The protocol was therefore
  designed to work **without** Actions. If Actions are in fact enabled, the
  bundled workflow in `.github/workflows/collaboration-verify.yml` is a bonus
  gate, not a dependency.
* **Whether ChatGPT has GitHub write access in your account.** This depends on
  your ChatGPT plan and connected apps. `PROTOCOL.md` §7.2 handles both cases and
  names the fallback.

### 4.3 Other OpenHands Cloud capabilities examined

* `GET /api/v1/app-conversations/{id}/hooks` reads hooks from a conversation's
  `.openhands/hooks.json` — this is about lifecycle hooks inside a running
  conversation, not GitHub triggers.
* `POST /api/v1/webhooks/conversations` is an inbound callback that OpenHands
  calls; it is not a way for us to subscribe to GitHub.
* `POST /integration/github/events` is the endpoint GitHub calls. It requires an
  `x-hub-signature-256` HMAC, which only GitHub and OpenHands can produce. We
  must not call it.
* There is no cron, scheduler or automation API in the OpenHands OpenAPI spec
  (242 paths searched). Background polling is therefore **not** available as a
  platform feature, and this design does not rely on it.

---

## 5. Constraints that must not be violated

1. `main` is frozen.
2. The MCP bridge stays read-only. No write or execute tool may be added to it.
3. No paid dependency or paid service.
4. No secret may be committed, printed, or placed in a task document. The
   engine enforces this; do not weaken `scan_secrets`.
5. Unrelated Lawnchair files are not touched.
6. Nothing merges automatically.

---

## 6. Environment notes

* Python 3.13. `uv` is available. There is no `zip` binary — use
  `python3 scripts/build_archive.py` style tooling.
* The MCP bridge venv is `mcp-bridge/.venv`. Tests run with
  `PYTHONPATH=src .venv/bin/python -m pytest` from `mcp-bridge/`.
* `collab.py` needs no virtualenv at all: standard library only, run it with
  `python3`.
* Sandbox helpers from earlier phases live in `/tmp` and are not committed.

---

## 7. Where to look next

| I want to… | Read |
| --- | --- |
| understand the rules | `collaboration/PROTOCOL.md` |
| see open work | `collaboration/INBOX.md` |
| see results to review | `collaboration/OUTBOX.md` |
| see the whole board | `collaboration/CURRENT_STATUS.md` |
| read a specific result | `collaboration/reports/<TASK_ID>.md` |
| inspect one task | `collaboration/tasks/<TASK_ID>.json` |
| understand the bridge | `mcp-bridge/docs/ARCHITECTURE.md` |
| understand bridge security | `mcp-bridge/docs/SECURITY.md` |
| connect ChatGPT to the bridge | `mcp-bridge/docs/CHATGPT_CONNECTOR.md` |
| read the previous handoff | `mcp-bridge/FINAL_HANDOFF_FOR_CHATGPT.txt` |
