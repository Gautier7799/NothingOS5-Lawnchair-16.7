# AGENTS.md — repository notes

## Layout

```
NothingOS5-Lawnchair-16.7/     # git root; only LICENSE + README.md upstream
├── mcp-bridge/                # the OpenHands Cloud MCP bridge (self-contained)
│   ├── src/openhands_mcp_bridge/
│   ├── tests/
│   ├── docs/                  # ARCHITECTURE, SECURITY, DEPLOYMENT, CHATGPT_CONNECTOR, VERIFICATION
│   ├── deploy/                # Caddyfile, nginx.conf, k8s.yaml
│   ├── scripts/build_archive.py
│   ├── Dockerfile, docker-compose.yml, Makefile, pyproject.toml
│   └── .env.example
├── collaboration/             # free GitHub handoff between ChatGPT and OpenHands
│   ├── PROTOCOL.md            # the contract — read this first
│   ├── ARCHITECTURE.md        # design + why alternatives were rejected
│   ├── PROJECT_CONTEXT.md     # durable memory across sessions
│   ├── README.md              # entry point
│   ├── INBOX.md               # GENERATED: open tasks
│   ├── OUTBOX.md              # GENERATED: results to review
│   ├── CURRENT_STATUS.md      # GENERATED: whole board
│   ├── ledger.jsonl           # append-only transition audit
│   ├── tasks/<ID>.json        # source of truth, one file per task
│   ├── reports/<ID>.md        # result detail (TEMPLATE.md)
│   ├── tools/collab.py        # the engine (stdlib only, offline)
│   └── tests/test_collab.py   # 39 engine tests
└── .github/                   # issue template + optional CI
```

## Branches

| Branch | Meaning |
| --- | --- |
| `main` | Upstream `Initial commit`. **Frozen — never modify.** |
| `mcp-bridge` | The MCP bridge and its docs. |
| `collaboration-hub` | The collaboration protocol (branched from `mcp-bridge`). |

All new work goes on `collaboration-hub`. Nothing merges to `main` without the
approval gate in `collaboration/PROTOCOL.md` §4.2.


## Commands

Everything runs from `mcp-bridge/`. There is a venv at `mcp-bridge/.venv`.

```bash
PYTHONPATH=src .venv/bin/python -m pytest            # 83 tests, no network
uvx ruff check src tests                             # must stay clean
PYTHONPATH=src .venv/bin/python -m openhands_mcp_bridge check   # needs the API key
PYTHONPATH=src .venv/bin/python -m openhands_mcp_bridge http    # serve on :12000
python3 scripts/build_archive.py                     # dist/openhands-mcp-bridge-1.0.0.zip
```

`make help` lists the same targets (`make archive` builds the ZIP).

## Collaboration protocol

Runs from the repository root, standard library only, no venv needed.

```bash
python3 collaboration/tests/test_collab.py       # 39 tests, offline
python3 collaboration/tools/collab.py verify     # integrity: evidence, approval, ledger
python3 collaboration/tools/collab.py scan       # secret scan (add --all for the repo)
python3 collaboration/tools/collab.py list --status PENDING
python3 collaboration/tools/collab.py render     # regenerate INBOX/OUTBOX/CURRENT_STATUS
```

OpenHands is the only writer of `tasks/*.json` and the three views. ChatGPT
reads them over `raw.githubusercontent.com` and writes to GitHub issues. Full
rules: `collaboration/PROTOCOL.md`. Platform findings: `collaboration/PROJECT_CONTEXT.md` §4.


## Live verification

The bridge was deployed on the sandbox host and exercised against the real
OpenHands Cloud API. `docs/VERIFICATION.md` holds the raw evidence. Two real
defects were found that way and are now fixed, with regression tests:

* an unknown conversation id returns `[null]` with HTTP 200 upstream, which
  used to surface as an `AttributeError`; it now maps to `code: not_found`.
* unexpected exceptions used to be stringified into the tool result by FastMCP;
  `server.py::_dispatch` now returns a generic `upstream_error` payload and
  logs the detail server-side.

Helper scripts used for live checks live in `/tmp` (not committed):
`/tmp/deploy-bridge.sh`, `/tmp/verify-public.sh`, `/tmp/live_e2e.py`,
`/tmp/live_pagination.py`, `/tmp/live_redaction.py`, `/tmp/mcp_helpers.py`.

## Hard rules for this project

* **Read-only.** Only `GET` may be issued to OpenHands Cloud. Never add a tool
  that sends messages, runs commands, edits files or creates conversations.
* **Never hardcode or print a secret.** `OPENHANDS_CLOUD_API_KEY` and
  `MCP_AUTH_TOKEN` come from the environment or a `*_FILE` mount. `Config.__repr__`
  redacts them; keep it that way. Add a redaction test with any new field.
* **Fail closed.** The server must refuse to start when no auth mode is
  configured (`MCP_REQUIRE_AUTH=true` is the default).
* **Tests use a fake upstream.** `tests/conftest.py` serves a Starlette app
  through `httpx.ASGITransport`; the suite must stay offline.
* Do not modify files outside `mcp-bridge/`.

## OpenHands Cloud API notes

* Base: `https://app.all-hands.dev`, API prefix `/api/v1`.
* Auth header: `Authorization: Bearer <key>` (OpenAPI declares an `APIKeyHeader`
  named `X-Access-Token`; the bearer form works).
* Endpoints used: `GET /app-conversations`, `GET /app-conversations/search`,
  `GET /conversation/{id}/events/search`, `GET /conversation/{id}/events/count`,
  `GET /app-conversations/{id}/download`.
* List/search responses are `{"items": [...], "next_page_id": "..."}`.
* `/download` returns a ZIP of `events/*.json` plus `meta.json`.
* `session_api_key` and `conversation_url` are stripped from summaries — they
  grant direct sandbox access.

## Environment

Python 3.13, `uv` available, no `zip` binary (use `scripts/build_archive.py`),
no `pip` inside the venv (`uv pip install --python .venv/bin/python`).
