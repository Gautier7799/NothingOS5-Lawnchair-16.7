# OpenHands Cloud MCP Bridge

A **read-only** remote MCP server that lets ChatGPT query OpenHands Cloud
conversations. It speaks the MCP **Streamable HTTP** transport, authenticates
every request at the edge, and never exposes credentials.

- Upstream API: OpenHands Cloud REST API v1 — `https://app.all-hands.dev/api/v1`
- Transport: MCP Streamable HTTP (`POST`/`GET`/`DELETE` on `/mcp`)
- Access model: **read-only** — no tool can send messages, run commands,
  change files or create conversations

---

## Tools

| Tool | Purpose |
| --- | --- |
| `openhands_list_conversations` | List conversations with pagination + filters |
| `openhands_get_conversation` | Full metadata record for one conversation |
| `openhands_get_conversation_status` | Compact status summary + event count |
| `openhands_get_conversation_events` | Read events (messages, actions, observations) |
| `openhands_download_trajectory` | Download + parse the trajectory archive |

All five accept `conversation_id`, which defaults to
`7177c0cd42934b7db0ec620706f3f547` so ChatGPT can call them with no arguments.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the request flow and
[docs/SECURITY.md](docs/SECURITY.md) for the threat model.

---

## Quick start (local)

```bash
cd mcp-bridge
uv venv .venv
uv pip install --python .venv/bin/python -e ".[dev]"

# Secrets — never put these in source control.
export OPENHANDS_CLOUD_API_KEY="<your OpenHands Cloud API key>"
export MCP_AUTH_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"

# Verify configuration + upstream connectivity
PYTHONPATH=src .venv/bin/python -m openhands_mcp_bridge check

# Serve MCP over Streamable HTTP on :12000
PYTHONPATH=src .venv/bin/python -m openhands_mcp_bridge http
```

Endpoints:

| Path | Auth | Purpose |
| --- | --- | --- |
| `/mcp` | required | MCP Streamable HTTP transport |
| `/healthz` | public | Liveness probe |
| `/readyz` | public | Readiness probe |
| `/.well-known/oauth-protected-resource` | public | RFC 9728 metadata |
| `/.well-known/oauth-authorization-server` | public | OAuth metadata (when configured) |

---

## Configuration

Every value is an environment variable. See [.env.example](.env.example).

| Variable | Default | Meaning |
| --- | --- | --- |
| `OPENHANDS_CLOUD_API_KEY` | *(required)* | OpenHands Cloud API key |
| `OPENHANDS_API_KEY` | — | Legacy fallback for the key above |
| `OPENHANDS_BASE_URL` | `https://app.all-hands.dev` | Upstream base URL |
| `MCP_AUTH_TOKEN` | — | Bearer token clients must present |
| `MCP_AUTH_TOKENS` | — | Comma-separated additional tokens |
| `MCP_OAUTH_ISSUER` | — | Enable OAuth 2.1 JWT validation |
| `MCP_OAUTH_AUDIENCE` | — | Expected `aud` claim |
| `MCP_REQUIRE_AUTH` | `true` | Refuse to start without an auth mode |
| `MCP_HOST` / `MCP_PORT` | `0.0.0.0` / `12000` | Listen address |
| `MCP_PATH` | `/mcp` | Transport path |
| `MCP_RESOURCE_URL` | derived | Public URL advertised in metadata |
| `MCP_RATE_LIMIT_REQUESTS` | `60` | Requests per window per principal |
| `MCP_RATE_LIMIT_WINDOW_SECONDS` | `60` | Window length |
| `MCP_MAX_EVENTS_RETURNED` | `1000` | Hard cap on events per call |
| `MCP_MAX_TRAJECTORY_DOWNLOAD_BYTES` | `134217728` | Trajectory archive size cap |
| `OPENHANDS_HTTP_TIMEOUT` | `30` | Upstream read timeout (s) |
| `OPENHANDS_HTTP_MAX_RETRIES` | `3` | Retries for transient upstream failures |
| `MCP_TRUSTED_HOSTS` | — | Optional `Host` allow-list |
| `MCP_LOG_LEVEL` | `INFO` | Log verbosity |

### Configuring `OPENHANDS_CLOUD_API_KEY` as a private secret

Never paste the key into source code, a Dockerfile, a compose file, a git
commit or a chat message. Choose one of:

**Local development**

```bash
cp .env.example .env        # .env is git-ignored
chmod 600 .env              # keep it private
# edit .env, then:
set -a; . ./.env; set +a
```

**Docker / Compose**

```bash
printf '%s' "$OPENHANDS_CLOUD_API_KEY" > secrets/openhands_cloud_api_key
printf '%s' "$(python -c 'import secrets; print(secrets.token_urlsafe(32))')" > secrets/mcp_auth_token
chmod 600 secrets/*
docker compose up -d
```

Compose mounts both files as Docker secrets at `/run/secrets/*`. The container
reads them through `*_FILE` variables, so the values never appear in
`docker inspect`, in the image, or in process arguments.

**Kubernetes**

```bash
kubectl create secret generic openhands-mcp-bridge \
  --from-literal=OPENHANDS_CLOUD_API_KEY="$OPENHANDS_CLOUD_API_KEY" \
  --from-literal=MCP_AUTH_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
```

Then reference it with `envFrom.secretRef` (see `deploy/k8s.yaml`).

**Cloud secret managers** — store the value in AWS Secrets Manager, GCP Secret
Manager, Azure Key Vault, or Vault, and inject it into the process environment
at runtime.

---

## Deployment status

A reference instance of this bridge was deployed and exercised end-to-end over
public HTTPS during development. The evidence — exact commands, raw outputs,
live tool results, redaction checks and log scans — is recorded in
[docs/VERIFICATION.md](docs/VERIFICATION.md).

| Item | Status |
| --- | --- |
| Source, tests, docs, deploy configs | complete in this repository |
| Unit/integration tests | 83 passing, no network |
| Lint | `ruff check src tests` clean |
| Public HTTPS MCP endpoint | verified live (see VERIFICATION.md) |
| All five tools against live OpenHands Cloud | verified live |
| Redaction of live payloads | verified live |
| ChatGPT connector created | **not** — requires your ChatGPT account |

The bridge does not ship with a permanent public URL: it is meant to run on
*your* host behind *your* TLS certificate. `deploy/Caddyfile`,
`deploy/nginx.conf` and `deploy/k8s.yaml` are provided for that. Follow
[docs/CHATGPT_CONNECTOR.md](docs/CHATGPT_CONNECTOR.md) once your endpoint is up.

---

## Tests

```bash
cd mcp-bridge
PYTHONPATH=src .venv/bin/python -m pytest -q
```

The suite covers authentication (accept/reject/rate-limit), conversation and
event retrieval over a real in-process MCP session, upstream failure handling,
redaction, size caps and input validation. It runs against a fake OpenHands API
and makes no network calls.

---

## Deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for HTTPS, Docker, reverse proxy
and Kubernetes recipes, and
[docs/CHATGPT_CONNECTOR.md](docs/CHATGPT_CONNECTOR.md) for the exact ChatGPT
connector steps.

---

## License

MIT — see the repository `LICENSE` file.
