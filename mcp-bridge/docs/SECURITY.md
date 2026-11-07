# Security review

Scope: the `openhands-mcp-bridge` service and the data it handles. This
document states what the bridge defends against, what it explicitly does not,
and the controls that back each claim.

## Assets

| Asset | Sensitivity | Where it lives |
| --- | --- | --- |
| `OPENHANDS_CLOUD_API_KEY` | High — full account access | Process env or a mounted secret file |
| `MCP_AUTH_TOKEN` | High — grants MCP access | Process env or a mounted secret file |
| Conversation content (messages, file paths, commands) | Medium — may contain proprietary code | In transit, in memory, in model context |
| `session_api_key` / `conversation_url` | High — direct sandbox access | Upstream only; stripped by the bridge |

## Threat model

| # | Threat | Control |
| --- | --- | --- |
| T1 | An unauthenticated internet client reaches the tools | `EndpointSecurityMiddleware` rejects before the MCP layer; server refuses to start without an auth mode |
| T2 | An attacker brute-forces the MCP token | 256-bit tokens from `secrets.token_urlsafe(32)`; constant-time comparison; per-principal rate limiting |
| T3 | The MCP token leaks through logs | Tokens are never logged; auth failures log a reason code only |
| T4 | The OpenHands API key leaks through errors | `redact_text` replaces the key and credential-shaped strings in every upstream error body |
| T5 | The API key leaks through tool output | Every tool result passes through `redact()`; `api_key` and friends become `**********` |
| T6 | The bridge escalates to write access | `client.py` issues `GET` only; there is no write path to enable |
| T7 | A malicious `conversation_id` escapes the endpoint | IDs are validated against `[A-Za-z0-9_-]{1,64}` and URL-quoted |
| T8 | A huge response exhausts memory | Size caps on pages, single events, responses and the trajectory archive (streamed, abandoned past the cap) |
| T9 | Runaway agent loops exhaust the upstream quota | Per-principal rate limit plus a separate upstream limiter |
| T10 | Secret exposure via the container image or `docker inspect` | Docker/K8s secret mounts read through `*_FILE`; nothing is baked into the image |
| T11 | Secret exposure via version control | `.gitignore` excludes `.env`, `secrets/`, keys; only `.env.example` is committed |
| T12 | Prompt injection makes the model exfiltrate the key | The key never enters the model's context; tool output is redacted before it is returned |
| T13 | Traffic interception | TLS terminated by the reverse proxy; HSTS enabled in the provided configs |
| T14 | A forged OAuth token grants access | JWTs are verified against the issuer's JWKS (signature, `iss`, optional `aud`) |

## Controls in detail

### Endpoint authentication (fail closed)

Two modes, combinable:

1. **Static bearer token.** Only the SHA-256 hash of each token is retained
   after startup, and comparison is constant time (`hmac.compare_digest`).
2. **OAuth 2.1 / OIDC.** Access tokens are verified as JWTs against the
   issuer's JWKS, checking signature, `iss` and (when configured) `aud`. The
   server publishes RFC 9728 protected-resource metadata and RFC 8414
   authorization-server metadata so ChatGPT can run the authorization-code +
   PKCE flow.

`MCP_REQUIRE_AUTH=true` (default) makes the process exit at startup if no auth
mode is configured. `Authenticator.authenticate` also fails closed if it is
somehow reached with no scheme while auth is required.

### Read-only guarantee

The only HTTP verb in `client.py` is `GET`. The tools that could mutate state —
`POST /app-conversations`, `POST /send-message`, `PATCH`/`DELETE` on
`/app-conversations/{id}` — are never referenced. A test asserts that every
request recorded by the fake upstream uses `GET`.

### Secret handling

* The API key is read once at startup and held in memory.
* `Config.__repr__` prints `<redacted:N chars>` instead of the value, so
  tracebacks and log lines cannot leak it.
* `redact()` walks nested dicts/lists and replaces any key matching
  `*api_key`, `*_secret`, `*_token`, `authorization`, `password`,
  `provider_tokens`, `session_api_key`, and the fixed list in
  `redaction.py`. It also scrubs `sk-…`, `sess-…`, `Bearer …` and JWT-shaped
  substrings inside free-form text.
* The configured key is additionally passed as an extra literal to scrub.
* No tool, route or log statement returns the key.

### Input validation

Every tool argument is validated by a pydantic model with explicit bounds.
`conversation_id` is restricted to letters, digits, `-` and `_`, capped at 64
characters, then URL-quoted before it is placed in a path. `limit` is clamped
to the configured maximum. `sort_order` is restricted to the two upstream
values. Invalid input returns `code: invalid_input`, never a 500.

### Error handling

Upstream failures map to stable codes: `upstream_unauthorized` (401/403),
`not_found` (404 **and** the `[null]` body OpenHands Cloud returns for an
unknown id), `invalid_input` (422), `upstream_timeout`, `upstream_error`,
`response_too_large`. Messages are generic; bodies are truncated to 400 bytes
and redacted. No upstream HTML, stack trace or credential is passed through.

The tool dispatcher also wraps *unexpected* exceptions: a non-`BridgeError`
failure is logged in full server-side and returned to the model as a generic
`upstream_error` payload, so FastMCP can never stringify an internal exception
(such as an `AttributeError` naming an internal object) into the tool result.

### Transport hardening

* The provided Docker image runs as a non-root user (uid 10001) with
  `no-new-privileges`, all capabilities dropped and a read-only root filesystem.
* Caddy/nginx configs set HSTS, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: no-referrer`, hide server tokens and cap the request body
  at 1 MB.
* The Kubernetes manifest sets `runAsNonRoot`, `seccompProfile:
  RuntimeDefault`, `allowPrivilegeEscalation: false`, a read-only root
  filesystem and resource limits.

## Residual risks and limitations

1. **Single-tenant bridge.** The service holds one OpenHands API key. Anyone who
   can authenticate to the MCP endpoint can read every conversation that key can
   see. Do not hand the token to untrusted parties; issue one bridge per trust
   boundary.
2. **In-process rate limiting.** Counters are per process. With multiple
   replicas, add a limiter at the ingress (the Caddy/nginx configs are the right
   place) for a global limit.
3. **Sensitive content still reaches the model.** Redaction targets credentials
   and the API key; it cannot know that a diff or a command output is
   confidential. Treat conversation content as data you are willing to place in
   a model context.
4. **Trajectory archives are large.** A trajectory can contain thousands of
   events and file contents. The bridge caps what it returns, but the download
   itself still transits the bridge. Consider lowering
   `MCP_MAX_TRAJECTORY_DOWNLOAD_BYTES` for stricter deployments.
5. **Static tokens do not expire.** Rotate `MCP_AUTH_TOKEN` on a schedule and
   restart the service. OAuth mode gives you revocation for free.
6. **No audit log of who read what.** Failed and successful tool calls are
   logged at `INFO` with the tool name and outcome code, but not the caller
   identity or conversation id. Add structured audit logging if you need
   attribution.

## Security checklist for operators

- [ ] `OPENHANDS_CLOUD_API_KEY` supplied through a secret mount or a secret
      manager — never in an image, a compose file or git.
- [ ] `MCP_AUTH_TOKEN` generated with `secrets.token_urlsafe(32)` or better.
- [ ] TLS terminated in front of the bridge, with HSTS enabled.
- [ ] `MCP_RESOURCE_URL` set to the public HTTPS URL.
- [ ] `MCP_REQUIRE_AUTH=true` (the default) left in place.
- [ ] Logs shipped somewhere with access control; verify no token appears.
- [ ] Token rotation scheduled.
- [ ] `pip-audit` / Dependabot watching `pyproject.toml`.
