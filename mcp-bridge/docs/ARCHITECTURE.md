# Architecture

## Overview

```
   ChatGPT
      │  HTTPS + Bearer token (MCP Streamable HTTP)
      ▼
┌──────────────────────────────────────────────────────────┐
│ Reverse proxy (Caddy / nginx / cloud LB) — TLS 1.2+      │
└──────────────────────────────────────────────────────────┘
      │  plain HTTP on the loopback / cluster network
      ▼
┌──────────────────────────────────────────────────────────┐
│ openhands-mcp-bridge (single ASGI process)               │
│                                                          │
│  EndpointSecurityMiddleware   ← authn + rate limit       │
│        │                                                 │
│  StreamableHTTPSessionManager ← MCP protocol             │
│        │                                                 │
│  FastMCP tool handlers (5 read-only tools)               │
│        │                                                 │
│  ToolService  ← pydantic validation                      │
│        │                                                 │
│  OpenHandsClient ← GET-only, retries, caps, redaction    │
└──────────────────────────────────────────────────────────┘
      │  HTTPS + Bearer <OPENHANDS_CLOUD_API_KEY>
      ▼
   https://app.all-hands.dev/api/v1
```

## Module map

| Module | Responsibility |
| --- | --- |
| `config.py` | Environment parsing, secret-file indirection, fail-closed validation |
| `errors.py` | Typed errors with stable `code` values and safe `to_dict()` |
| `redaction.py` | Recursive redaction of sensitive keys and credential-looking values |
| `rate_limit.py` | Async token-bucket limiter (per principal, per process) |
| `auth.py` | Static bearer tokens (hashed, constant-time) and OAuth JWT verification |
| `client.py` | Read-only OpenHands Cloud v1 client |
| `tools.py` | Tool argument models, tool registry, `ToolService` |
| `server.py` | FastMCP wiring, security middleware, ASGI assembly |
| `__main__.py` | CLI: `http`, `stdio`, `check` |

## Request lifecycle

1. The client `POST`s a JSON-RPC message to `/mcp` with
   `Authorization: Bearer <token>`.
2. `EndpointSecurityMiddleware` (raw ASGI, so streams and lifespan pass
   through) authenticates the token and applies the rate limit. A failure
   short-circuits with `401` or `429` before the MCP layer is reached.
3. `StreamableHTTPSessionManager` decodes the message and dispatches it to the
   `FastMCP` server.
4. `FastMCP` validates arguments against the tool's signature, then calls the
   handler.
5. The handler delegates to `ToolService`, which re-validates with pydantic and
   calls `OpenHandsClient`.
6. `OpenHandsClient` issues a `GET` to OpenHands Cloud with the API key, with
   timeouts, retries and a size cap.
7. The response is redacted, then returned to the client.

Errors never abort the MCP session: they are raised as a `ToolError` whose text
is a JSON payload such as
`{"ok": false, "code": "upstream_error", "message": "..."}`. The MCP client sees
`isError: true` and the model can read the machine-readable `code`.

## Transport choice

MCP Streamable HTTP is the transport ChatGPT's remote-connector support speaks.
The server runs in **stateless** mode (`stateless_http=True`), so any replica can
answer any request and horizontal scaling needs no sticky sessions.

## Upstream endpoints used

| Tool | Method + path |
| --- | --- |
| `openhands_list_conversations` | `GET /api/v1/app-conversations/search` |
| `openhands_get_conversation` | `GET /api/v1/app-conversations?ids=<id>` |
| `openhands_get_conversation_status` | `GET /api/v1/app-conversations?ids=<id>` + `GET /api/v1/conversation/<id>/events/count` |
| `openhands_get_conversation_events` | `GET /api/v1/conversation/<id>/events/search` |
| `openhands_download_trajectory` | `GET /api/v1/app-conversations/<id>/download` |

Only `GET` is ever used. `client.py` contains no other HTTP verb.

## Pagination

`app-conversations/search` and `events/search` return
`{"items": [...], "next_page_id": "..."}`. The client exposes both `page_id`
(pass the opaque cursor back) and `max_pages` (follow the cursor
automatically, bounded by the tool's `ge`/`le` constraints). Every call also
carries a hard cap (`MCP_MAX_EVENTS_RETURNED`, `max_events`) so a single tool
call cannot pull an unbounded stream into the model's context.

## Limits and their rationale

| Limit | Why |
| --- | --- |
| `max_event_page_limit` = 100 | Matches the upstream maximum; larger values are rejected upstream |
| `max_events_returned` = 1000 | Bounds one tool call's context cost |
| `max_event_bytes` / `max_response_bytes` | Prevents a single event or page from exhausting memory |
| `max_trajectory_download_bytes` = 128 MiB | The archive is streamed and abandoned past the cap |
| `max_trajectory_events_returned` = 500 | Trajectory archives can hold thousands of events |
| `rate_limit_requests` = 60/min | Protects the upstream account from runaway agent loops |

## Data minimisation

`conversation_url` and `session_api_key` are dropped from conversation
summaries. Those fields grant direct sandbox access and are not needed to read
a conversation. They remain in the raw record only in redacted form.
