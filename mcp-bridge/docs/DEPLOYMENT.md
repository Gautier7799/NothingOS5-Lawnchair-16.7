# Deployment

Three supported paths. All of them require two secrets:

| Secret | How to generate |
| --- | --- |
| `OPENHANDS_CLOUD_API_KEY` | OpenHands Cloud → Settings → API keys |
| `MCP_AUTH_TOKEN` | `python -c 'import secrets; print(secrets.token_urlsafe(32))'` |

Never place either value in a Dockerfile, a compose file, a git commit or a chat
message. The recipes below use secret mounts or a secret manager.

---

## 1. Docker Compose + Caddy (recommended for a single host)

```bash
cd mcp-bridge

# 1. Secrets on disk, readable only by you.
mkdir -p secrets
printf '%s' "$OPENHANDS_CLOUD_API_KEY" > secrets/openhands_cloud_api_key
printf '%s' "$(python -c 'import secrets; print(secrets.token_urlsafe(32))')" \
  > secrets/mcp_auth_token
chmod 600 secrets/*

# 2. Point the domain at this host, then edit deploy/Caddyfile's hostname.
export MCP_RESOURCE_URL="https://mcp.example.com/mcp"

# 3. Start the bridge (binds 127.0.0.1:12000 only).
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:12000/healthz

# 4. Terminate TLS in front of it.
docker run -d --name caddy --restart unless-stopped \
  -p 80:80 -p 443:443 \
  -v "$PWD/deploy/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -v caddy_data:/data -v caddy_config:/config \
  caddy:2
```

Verify the public endpoint:

```bash
curl -fsS https://mcp.example.com/healthz
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://mcp.example.com/mcp
# -> 401, which proves the endpoint is reachable *and* authenticated.
```

---

## 2. Systemd + nginx (no containers)

```bash
# --- Install ---
sudo useradd --system --create-home --shell /usr/sbin/nologin openhands-mcp
sudo mkdir -p /opt/openhands-mcp-bridge /etc/openhands-mcp
sudo cp -r src pyproject.toml README.md /opt/openhands-mcp-bridge/
sudo python3 -m venv /opt/openhands-mcp-bridge/.venv
sudo /opt/openhands-mcp-bridge/.venv/bin/pip install /opt/openhands-mcp-bridge

# --- Secrets ---
sudo install -m 0600 -o openhands-mcp -g openhands-mcp /dev/null /etc/openhands-mcp/api-key
sudo install -m 0600 -o openhands-mcp -g openhands-mcp /dev/null /etc/openhands-mcp/mcp-token
sudo sh -c 'read -r k; printf %s "$k" > /etc/openhands-mcp/api-key' <<< "$OPENHANDS_CLOUD_API_KEY"
sudo sh -c 'printf %s "$(python3 -c "import secrets;print(secrets.token_urlsafe(32))")" > /etc/openhands-mcp/mcp-token'

# --- Service ---
sudo tee /etc/systemd/system/openhands-mcp-bridge.service >/dev/null <<'UNIT'
[Unit]
Description=OpenHands Cloud MCP Bridge
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=openhands-mcp
WorkingDirectory=/opt/openhands-mcp-bridge
Environment=OPENHANDS_CLOUD_API_KEY_FILE=/etc/openhands-mcp/api-key
Environment=MCP_AUTH_TOKEN_FILE=/etc/openhands-mcp/mcp-token
Environment=MCP_RESOURCE_URL=https://mcp.example.com/mcp
Environment=MCP_HOST=127.0.0.1
Environment=MCP_PORT=12000
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/tmp
ExecStart=/opt/openhands-mcp-bridge/.venv/bin/python -m openhands_mcp_bridge http
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable --now openhands-mcp-bridge
systemctl status openhands-mcp-bridge --no-pager
```

Then install `deploy/nginx.conf` (adjust the hostname and certificate paths) and
`certbot certonly --nginx -d mcp.example.com`.

---

## 3. Kubernetes

```bash
kubectl create secret generic openhands-mcp-bridge \
  --from-literal=OPENHANDS_CLOUD_API_KEY="$OPENHANDS_CLOUD_API_KEY" \
  --from-literal=MCP_AUTH_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"

kubectl apply -f deploy/k8s.yaml
kubectl rollout status deploy/openhands-mcp-bridge
```

Expose the `openhands-mcp-bridge` Service through your ingress controller with
TLS and `MCP_RESOURCE_URL` set to the public URL. Disable proxy buffering for
the ingress route so MCP event streams are not held back.

---

## Verifying a deployment

```bash
BASE=https://mcp.example.com
TOKEN=<the MCP_AUTH_TOKEN value>

# Liveness (public)
curl -fsS "$BASE/healthz"

# Authentication is enforced
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/mcp"        # 401
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/mcp" \
  -H "Authorization: Bearer wrong"                                   # 401

# Authenticated MCP handshake
curl -sS -X POST "$BASE/mcp" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize",
       "params":{"protocolVersion":"2025-06-18","capabilities":{},
                 "clientInfo":{"name":"curl","version":"1"}}}'
```

The final command should return an `initialize` result naming
`openhands-cloud-mcp-bridge`.

To exercise a tool with the official MCP client:

```bash
python - <<'PY'
import anyio
import httpx
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

URL = "https://mcp.example.com/mcp"
TOKEN = "<the MCP_AUTH_TOKEN value>"

async def main():
    async with httpx.AsyncClient(
        headers={"Authorization": f"Bearer {TOKEN}"}, timeout=60
    ) as client:
        async with streamable_http_client(URL, http_client=client) as (r, w, _):
            async with ClientSession(r, w) as session:
                await session.initialize()
                tools = await session.list_tools()
                print([t.name for t in tools.tools])
                result = await session.call_tool("openhands_get_conversation_status", {})
                print(result.structuredContent)

anyio.run(main)
PY
```

---

## Operations

| Task | Command |
| --- | --- |
| Liveness | `GET /healthz` |
| Readiness | `GET /readyz` |
| Rotate the MCP token | Update the secret, restart the service |
| Rotate the API key | Update the secret, restart the service |
| Inspect logs | `docker compose logs -f` / `journalctl -u openhands-mcp-bridge -f` |
| Check upstream reachability | `python -m openhands_mcp_bridge check` |

### Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| Container exits immediately with `configuration error` | `OPENHANDS_CLOUD_API_KEY` or an auth mode is missing |
| Every tool returns `upstream_unauthorized` | The OpenHands API key is wrong, expired or revoked |
| Every tool returns `upstream_timeout` | Network egress to `app.all-hands.dev` is blocked, or the proxy is buffering |
| `429` from the bridge | The per-principal rate limit was hit — raise `MCP_RATE_LIMIT_REQUESTS` or wait |
| ChatGPT reports the server is unreachable | TLS, DNS or the `/mcp` path is wrong; confirm with the `curl` checks above |
| ChatGPT returns 401 after the token was rotated | Recreate the connector credential with the new token |
