# Verification record

Every claim below was produced by running the command shown, in this
environment, against the code in this repository. Nothing here is projected or
assumed.

## 1. Test suite

```
$ cd mcp-bridge
$ PYTHONPATH=src .venv/bin/python -m pytest
83 passed in 5.64s
```

| Suite | Tests | Covers |
| --- | --- | --- |
| `tests/test_auth.py` | 8 | Static token accept/reject, missing header, empty token, fail-closed when auth is required but unconfigured, static/oauth precedence |
| `tests/test_oauth.py` | 6 | OAuth token acceptance, static short-circuit, unknown token, malformed JWT, unknown signing key |
| `tests/test_config.py` | 9 | Missing key, legacy fallback, auth required, repr redaction, defaults, secret-file loading, missing secret file |
| `tests/test_client.py` | 30 | Bearer header, conversation retrieval, pagination, only-GET enforcement, session-key stripping, event filtering/clamping, event count, status, trajectory ZIP parse, trajectory redaction, size cap, 401/404/500 mapping, redacted error bodies, malformed JSON, id validation, timeouts, retry behaviour, **unknown-id → `not_found`**, null-only/empty lookup responses |
| `tests/test_redaction.py` | 7 | Nested keys, lists, secret suffixes, credential-shaped text, extra secrets, short-value guard, non-strings |
| `tests/test_server.py` | 23 | Five-tool listing, schemas, each tool over a real MCP session, session-key redaction, missing/wrong token 401, unauthenticated handshake, public health endpoints, protected-resource metadata, structured upstream errors, 401 not leaked, invalid id/limit/unknown tool, **unknown id → `not_found`**, **unexpected exception → structured non-leaking error**, rate-limit 429 |

The suite runs against an in-process fake OpenHands Cloud API
(`tests/conftest.py`) and makes no outbound network calls.

Lint:

```
$ uvx ruff check src tests
All checks passed!
```

### Bugs found and fixed during verification

Two real defects were found by exercising the *live* endpoint, not the fake:

1. **Unknown conversation id was not a clean error.** OpenHands Cloud answers
   `GET /api/v1/app-conversations?ids=<unknown>` with `[null]` and HTTP 200,
   not a 404. The client iterated the list, skipped the `null`, found no
   matching record, then fell through to `records[0]` → `None`, and the tool
   surfaced `'NoneType' object has no attribute 'get'`. Fixed in
   `client.py::get_conversation` (filter to dict records, raise `NotFoundError`
   when nothing matches) with regression tests.
2. **Unexpected exceptions leaked internals.** Any non-`BridgeError` exception
   reached FastMCP, which stringifies it into the tool result
   (`Error executing tool …: <message>`). Fixed by catching `Exception` in
   `server.py::_dispatch` and returning a generic `upstream_error` payload
   while logging the detail server-side. Regression test asserts the raw
   message and a credential-shaped literal never reach the model.

Live confirmation of the fix:

```
unknown id     isError=True code=not_found     msg=Conversation 0000…0000 was not found.
malformed id   isError=True code=invalid_input msg=conversation_id may only contain letters, digits, '-' and '_'.
```


## 2. Configuration validation

```
$ PYTHONPATH=src OPENHANDS_CLOUD_API_KEY=... MCP_AUTH_TOKEN=x \
    .venv/bin/python -m openhands_mcp_bridge check
OpenHands Cloud reachable.
  conversation: 7177c0cd42934b7db0ec620706f3f547
  sandbox_status: RUNNING
  execution_status: finished
  event_count: 968
```

## 3. Public HTTPS endpoint

Base URL: `https://work-1-xahmymzqavnzfgpt.prod-runtime.all-hands.dev`

| Request | Result |
| --- | --- |
| `GET /healthz` | `200` `{"status":"ok","server":"openhands-cloud-mcp-bridge"}` |
| `GET /readyz` | `200` `{"status":"ready","upstream":"https://app.all-hands.dev"}` |
| `POST /mcp` (no token) | `401` `{"error":"invalid_token",...}` |
| `POST /mcp` (wrong token) | `401` `{"error":"invalid_token",...}` |
| `POST /mcp` (no token) headers | `www-authenticate: Bearer error="invalid_token", resource_metadata="…/.well-known/oauth-protected-resource"` |
| `GET /.well-known/oauth-protected-resource` | `200` `{"resource":"…/mcp","bearer_methods_supported":["header"],"scopes_supported":["openhands.read"]}` |

MCP endpoint (the value to give ChatGPT):

```
https://work-1-xahmymzqavnzfgpt.prod-runtime.all-hands.dev/mcp
```

## 4. Live end-to-end MCP session

Run through the official MCP SDK client against the public HTTPS URL, using a
token read from a `0600` file (never printed):

```
tools: ['openhands_download_trajectory', 'openhands_get_conversation',
        'openhands_get_conversation_events', 'openhands_get_conversation_status',
        'openhands_list_conversations']
openhands_list_conversations:        {"count": 3, "first_id": "27f6c70defc14a169a14cce09a81dc19"}
openhands_get_conversation:          {"id": "7177c0cd42934b7db0ec620706f3f547",
                                      "title": "READ-ONLY ENGINEERING AUDIT — Material Y",
                                      "sandbox_status": "RUNNING",
                                      "execution_status": "finished"}
openhands_get_conversation_status:   {"id": "7177c0cd42934b7db0ec620706f3f547",
                                      "sandbox_status": "RUNNING",
                                      "execution_status": "finished",
                                      "event_count": 969}
openhands_get_conversation_events:   {"count": 10,
                                      "kinds": ["ActionEvent", "ConversationStateUpdateEvent",
                                                "MessageEvent", "ObservationEvent",
                                                "SecurityAnalysisEvent", "SystemPromptEvent"],
                                      "next_page_id_present": true}
openhands_download_trajectory:       {"total_events": 967, "returned_events": 5,
                                      "truncated": true, "archive_bytes": 3558341}

LIVE E2E OK
```

All five tools returned real data for the requested conversation id, with
`next_page_id` present (pagination working) and the trajectory archive
downloaded and parsed from the live upstream.

## 5. Redaction against live data

The live payloads were scanned for the OpenHands API key, `sk-…` tokens,
`Bearer …` tokens and JWTs:

```
openhands_get_conversation          openhands api key  clean
openhands_get_conversation          sk- token          clean
openhands_get_conversation          bearer token       clean
openhands_get_conversation          jwt                clean
openhands_get_conversation_events   openhands api key  clean
openhands_get_conversation_events   sk- token          clean
openhands_get_conversation_events   bearer token       clean
openhands_get_conversation_events   jwt                clean
openhands_list_conversations        openhands api key  clean
openhands_list_conversations        sk- token          clean
openhands_list_conversations        bearer token       clean
openhands_list_conversations        jwt                clean

REDACTION OK (no credentials in live tool output)
```

## 6. Log hygiene

The server log for a full E2E run was scanned for both secrets:

```
$ grep -c "$OPENHANDS_API_KEY" /tmp/oh-mcp-bridge.log
0
$ grep -c "$(cat /tmp/oh-mcp-secrets/mcp_auth_token)" /tmp/oh-mcp-bridge.log
0
```

Repository scan for committed secrets:

```
$ grep -rIl "$OPENHANDS_API_KEY" --exclude-dir=.venv .
(no matches)
$ grep -rIl "$(cat /tmp/oh-mcp-secrets/mcp_auth_token)" --exclude-dir=.venv .
(no matches)
$ grep -rInE "(api_key|token|secret)\s*=\s*[\"'][A-Za-z0-9_\-]{20,}" --exclude-dir=.venv .
(no matches)
```

## 7. Deployment status

The sandbox instance is running and publicly reachable over HTTPS through the
platform's TLS-terminating edge, as evidenced by section 3 and section 4.

What is **not** claimed:

* No TLS certificate was issued by this work — the platform edge terminates
  TLS. `deploy/Caddyfile` and `deploy/nginx.conf` are provided, unexecuted,
  for a production host with your own domain.
* No container image was built or run here; `Dockerfile` and
  `docker-compose.yml` are provided and are not exercised by this environment.
* No Kubernetes cluster was targeted; `deploy/k8s.yaml` is provided as-is.
* No ChatGPT connector was created — that requires your account. Follow
  `docs/CHATGPT_CONNECTOR.md`.

## 8. Reproducing this record

```bash
cd mcp-bridge
make install
make test
make check          # needs OPENHANDS_CLOUD_API_KEY
make run            # serves on 0.0.0.0:12000
```

## 9. Deliverable archive

`make archive` (or `python3 scripts/build_archive.py`) writes a source archive
containing the application, tests, docs and deploy configs — no secrets, no
virtualenv, no build output.

```
$ make archive
python3 scripts/build_archive.py
wrote /…/dist/openhands-mcp-bridge-1.0.0.zip (36 files, 61.1 KiB)

$ sha256sum dist/openhands-mcp-bridge-1.0.0.zip
21f750f871bf053af57f7a9e6c593e64e89e7b3daf86f21b500346f8c16817e1
```

The digest changes on every rebuild (ZIP stores timestamps), so treat the
value above as illustrative and recompute it after building. `dist/` is
git-ignored — the archive is a build artefact, not a committed file.

The archive was extracted to a clean directory, installed into a fresh
virtualenv and tested, to prove it is self-contained:

```
$ unzip -q dist/openhands-mcp-bridge-1.0.0.zip -d /tmp/zipcheck && cd /tmp/zipcheck
$ python -m venv .venv && .venv/bin/pip install -e ".[dev]"
$ PYTHONPATH=src .venv/bin/python -m pytest
83 passed in 5.47s
```

Scanning the extracted tree for the live API key and MCP token produced no
matches.
