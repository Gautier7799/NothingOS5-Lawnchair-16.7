# AGENTS.md — repository notes

## Layout

```
NothingOS5-Lawnchair-16.7/     # git root; only LICENSE + README.md upstream
└── mcp-bridge/                # the OpenHands Cloud MCP bridge (self-contained)
    ├── src/openhands_mcp_bridge/
    ├── tests/
    ├── docs/                  # ARCHITECTURE, SECURITY, DEPLOYMENT, CHATGPT_CONNECTOR, VERIFICATION
    ├── deploy/                # Caddyfile, nginx.conf, k8s.yaml
    ├── scripts/build_archive.py
    ├── Dockerfile, docker-compose.yml, Makefile, pyproject.toml
    └── .env.example
```

## Commands

Everything runs from `mcp-bridge/`. There is a venv at `mcp-bridge/.venv`.

```bash
PYTHONPATH=src .venv/bin/python -m pytest            # 77 tests, no network
uvx ruff check src tests                             # must stay clean
PYTHONPATH=src .venv/bin/python -m openhands_mcp_bridge check   # needs the API key
PYTHONPATH=src .venv/bin/python -m openhands_mcp_bridge http    # serve on :12000
python3 scripts/build_archive.py                     # dist/openhands-mcp-bridge-1.0.0.zip
```

`make help` lists the same targets.

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
