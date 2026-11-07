# Connecting ChatGPT to the MCP bridge

This is the operator-facing walkthrough. Two authentication modes are
supported; pick one.

* **API key (simplest, available today)** — you paste the `MCP_AUTH_TOKEN`
  into the connector dialog.
* **OAuth (no pasted secret)** — requires `MCP_OAUTH_ISSUER` to be configured
  on the bridge and a ChatGPT plan/workspace that supports OAuth connectors.

> Prerequisite: the bridge is deployed behind HTTPS and
> `https://<your-domain>/healthz` returns `{"status":"ok"}`. Verify with
> [DEPLOYMENT.md](DEPLOYMENT.md#verifying-a-deployment) first.

---

## A. API key mode

### 1. Get the endpoint URL

```
https://<your-domain>/mcp
```

This must be the *public* HTTPS URL, with the `/mcp` path. It must not be an
internal address or a bare domain.

### 2. Retrieve the MCP token

On the server, read the token you generated during deployment. It is the value
of `MCP_AUTH_TOKEN` (or the contents of `secrets/mcp_auth_token`). Treat it like
a password.

```bash
# On the deployment host:
sudo cat /etc/openhands-mcp/openhands-mcp-token   # systemd install
docker compose exec openhands-mcp-bridge cat /run/secrets/mcp_auth_token
```

### 3. Create the connector in ChatGPT

1. Open ChatGPT and go to **Settings → Connectors** (in some plans:
   **Settings → Beta features → Connectors**, or the **Apps** section).
2. Choose **Add custom connector** / **Create**.
3. Fill in:

   | Field | Value |
   | --- | --- |
   | Name | `OpenHands Cloud (read-only)` |
   | Description | `Read OpenHands Cloud conversations, events and trajectories` |
   | MCP server URL | `https://<your-domain>/mcp` |
   | Authentication | **API key** / **Bearer token** |

4. Paste the `MCP_AUTH_TOKEN` value into the API key / token field.
5. Save. ChatGPT performs an MCP `initialize` and `tools/list`; it should
   report five tools, all prefixed `openhands_`.

If the connector dialog offers only OAuth, use section B, or use a client that
accepts a custom `Authorization` header (for example the Python snippet in
[DEPLOYMENT.md](DEPLOYMENT.md#verifying-a-deployment)).

### 4. Use it

In a chat, enable the connector for the conversation (the tool picker / "Add
tools" menu), then ask:

```
List my five most recent OpenHands Cloud conversations.
```

```
Show me the status of conversation 7177c0cd42934b7db0ec620706f3f547.
```

```
Read the last 20 events of that conversation and summarise what the agent did.
```

The model calls `openhands_list_conversations`, `openhands_get_conversation_status`
and `openhands_get_conversation_events` respectively. `conversation_id` defaults
to `7177c0cd42934b7db0ec620706f3f547`, so a bare
"what is the status of my default conversation?" also works.

### 5. Rotate the token

1. On the server, generate a new token and update the secret file.
2. Restart the bridge.
3. In ChatGPT, edit the connector and paste the new token.

Requests with the old token then fail with `401`.

---

## B. OAuth mode

Configure the bridge:

```bash
MCP_OAUTH_ISSUER=https://your-idp.example.com
MCP_OAUTH_AUDIENCE=https://<your-domain>/mcp     # optional but recommended
MCP_RESOURCE_URL=https://<your-domain>/mcp
```

The bridge then:

* verifies access tokens as JWTs against the issuer's JWKS (`iss`, `aud`,
  signature),
* publishes `https://<your-domain>/.well-known/oauth-protected-resource`
  (RFC 9728) listing the authorization server,
* publishes `https://<your-domain>/.well-known/oauth-authorization-server`
  (RFC 8414) so clients can discover the authorization, token, registration and
  JWKS endpoints,
* returns `WWW-Authenticate: Bearer … resource_metadata="…"` on `401`, which is
  the discovery hint ChatGPT follows.

In ChatGPT's connector dialog choose **OAuth**, leave client credentials blank
(dynamic client registration) and complete the browser flow. The IDP must issue
access tokens with `iss` equal to `MCP_OAUTH_ISSUER` and, if you set an
audience, an `aud` containing `MCP_OAUTH_AUDIENCE`.

---

## C. Verifying the connector without ChatGPT

Use the official MCP SDK:

```bash
python - <<'PY'
import anyio
import httpx
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

URL = "https://<your-domain>/mcp"
TOKEN = "<MCP_AUTH_TOKEN>"

async def main():
    async with httpx.AsyncClient(
        headers={"Authorization": f"Bearer {TOKEN}"}, timeout=60
    ) as client:
        async with streamable_http_client(URL, http_client=client) as (r, w, _):
            async with ClientSession(r, w) as s:
                await s.initialize()
                print("tools:", sorted(t.name for t in (await s.list_tools()).tools))
                out = await s.call_tool("openhands_get_conversation_status", {})
                print("status:", out.structuredContent)

anyio.run(main)
PY
```

Expected: the five `openhands_*` tools and a status payload for conversation
`7177c0cd42934b7db0ec620706f3f547`.

---

## Error reference

| What ChatGPT shows | Code returned | Meaning / fix |
| --- | --- | --- |
| `401 Unauthorized` | — | Missing or wrong token; re-paste `MCP_AUTH_TOKEN` |
| `429 Too Many Requests` | — | Rate limit hit; wait, or raise `MCP_RATE_LIMIT_REQUESTS` |
| Tool error | `invalid_input` | Bad argument (for example a malformed `conversation_id`) |
| Tool error | `not_found` | No conversation with that id is visible to this API key |
| Tool error | `upstream_unauthorized` | The OpenHands API key is invalid, expired or revoked |
| Tool error | `upstream_timeout` | Egress to `app.all-hands.dev` is blocked or slow |
| Tool error | `upstream_error` | OpenHands Cloud returned a 5xx; retry shortly |
| Tool error | `response_too_large` | Lower the `limit`, or raise the relevant `MCP_MAX_*` cap |

## Privacy note

When you ask ChatGPT to read a conversation, that conversation's content is
placed in the model's context. The bridge redacts credentials (`api_key` fields,
`sk-…` strings, JWTs, the OpenHands API key itself), but it cannot judge whether
a diff, a log line or a file path is confidential. Only connect conversations
you are willing to send to the model.
