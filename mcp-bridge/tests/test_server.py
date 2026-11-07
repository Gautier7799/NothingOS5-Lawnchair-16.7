"""End-to-end tests: MCP protocol over HTTP + endpoint authentication."""

from __future__ import annotations

import json
from contextlib import asynccontextmanager

import httpx
import pytest
from mcp.client.session import ClientSession
from mcp.client.streamable_http import streamable_http_client

from openhands_mcp_bridge.tools import TOOL_NAMES

from .conftest import (
    TEST_CONVERSATION_ID,
    TEST_MCP_TOKEN,
    make_bridge_app,
    make_config,
)


def _http_client(app, headers: dict[str, str]) -> httpx.AsyncClient:
    return httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app),
        base_url="http://bridge",
        headers=headers,
        timeout=10,
    )


def _contains_status(error: BaseException, status: int) -> bool:
    """Walk an ExceptionGroup tree looking for an HTTPStatusError of ``status``."""

    if isinstance(error, httpx.HTTPStatusError):
        return error.response.status_code == status
    for nested in getattr(error, "exceptions", ()) or ():
        if _contains_status(nested, status):
            return True
    return False


@asynccontextmanager
async def bridge_session(fake_api_app, *, token: str | None = TEST_MCP_TOKEN, config=None):
    """Start the bridge app in-process and open an authenticated MCP session."""

    app = make_bridge_app(config or make_config(), httpx.ASGITransport(app=fake_api_app))
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    client = _http_client(app, headers)
    async with (
        app.router.lifespan_context(app),
        client,
        streamable_http_client("http://bridge/mcp", http_client=client) as (read, write, _),
        ClientSession(read, write) as session,
    ):
        await session.initialize()
        yield session


async def _call(session: ClientSession, name: str, arguments: dict) -> dict:
    result = await session.call_tool(name, arguments)
    if result.isError:
        # FastMCP prefixes the payload with "Error executing tool <name>: ".
        for content in result.content:
            text = getattr(content, "text", "")
            start = text.find("{")
            if start != -1:
                try:
                    payload = json.loads(text[start:])
                except json.JSONDecodeError:
                    continue
                payload.setdefault("ok", False)
                return payload
        return {"ok": False, "raw": [getattr(c, "text", "") for c in result.content]}
    structured = result.structuredContent
    if structured:
        # FastMCP wraps non-dict returns under {"result": ...}.
        return structured.get("result", structured)
    return json.loads(result.content[0].text)


# --------------------------------------------------------------- protocol
async def test_lists_exactly_the_five_readonly_tools(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        tools = await session.list_tools()
    names = sorted(t.name for t in tools.tools)
    assert names == sorted(TOOL_NAMES)
    assert names == [
        "openhands_download_trajectory",
        "openhands_get_conversation",
        "openhands_get_conversation_events",
        "openhands_get_conversation_status",
        "openhands_list_conversations",
    ]


async def test_tool_schemas_expose_conversation_id_default(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        tools = {t.name: t for t in (await session.list_tools()).tools}
    schema = tools["openhands_get_conversation"].inputSchema
    assert schema["properties"]["conversation_id"]["default"] == TEST_CONVERSATION_ID


# --------------------------------------------------------------- retrieval
async def test_get_conversation_tool(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_get_conversation", {})
    assert payload["ok"] is True
    assert payload["conversation"]["id"] == TEST_CONVERSATION_ID
    assert payload["conversation"]["sandbox_status"] == "RUNNING"


async def test_get_conversation_redacts_session_api_key(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_get_conversation", {})
    serialized = json.dumps(payload)
    assert "super-secret-session-key" not in serialized
    assert (
        "session_api_key" not in payload["raw"]
        or payload["raw"]["session_api_key"] == "**********"
    )


async def test_list_conversations_tool(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_list_conversations", {"limit": 5})
    assert payload["ok"] is True
    assert payload["count"] == 1
    assert payload["items"][0]["id"] == TEST_CONVERSATION_ID


async def test_get_conversation_status_tool(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_get_conversation_status", {})
    assert payload["ok"] is True
    assert payload["execution_status"] == "running"
    assert payload["event_count"] == 3


async def test_get_conversation_events_tool(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_get_conversation_events", {"limit": 10})
    assert payload["ok"] is True
    assert payload["count"] == 3
    assert payload["items"][0]["kind"] == "ConversationStateUpdateEvent"


async def test_get_conversation_events_redacts_api_keys_in_payload(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(
            session, "openhands_get_conversation_events", {"limit": 10, "exclude_full_state": False}
        )
    serialized = json.dumps(payload)
    assert "sk-leaked-should-be-redacted-000111" not in serialized
    assert "**********" in serialized


async def test_download_trajectory_tool(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_download_trajectory", {})
    assert payload["ok"] is True
    assert payload["total_events"] == 3
    assert payload["conversation_id"] == TEST_CONVERSATION_ID
    assert json.dumps(payload).find("sk-in-trajectory-should-be-redacted") == -1


# --------------------------------------------------------------- auth
async def test_missing_token_is_rejected_with_401(fake_api_app):
    app = make_bridge_app(make_config(), httpx.ASGITransport(app=fake_api_app))
    async with app.router.lifespan_context(app), httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://bridge"
    ) as client:
        response = await client.post(
            "/mcp",
            json={"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
            headers={"Accept": "application/json, text/event-stream"},
        )
    assert response.status_code == 401
    assert "www-authenticate" in {k.lower() for k in response.headers}


async def test_wrong_token_is_rejected_with_401(fake_api_app):
    app = make_bridge_app(make_config(), httpx.ASGITransport(app=fake_api_app))
    async with app.router.lifespan_context(app), httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://bridge"
    ) as client:
        response = await client.post(
            "/mcp",
            json={"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
            headers={
                "Authorization": "Bearer wrong-token",
                "Accept": "application/json, text/event-stream",
            },
        )
    assert response.status_code == 401


async def test_unauthenticated_mcp_client_cannot_initialize(fake_api_app):
    # The MCP client runs the handshake in a task group, so the 401 surfaces
    # wrapped in an ExceptionGroup.
    with pytest.raises((httpx.HTTPStatusError, ExceptionGroup)) as excinfo:
        async with bridge_session(fake_api_app, token=None):
            pass
    assert _contains_status(excinfo.value, 401)


async def test_health_endpoints_are_public(fake_api_app):
    app = make_bridge_app(make_config(), httpx.ASGITransport(app=fake_api_app))
    async with app.router.lifespan_context(app), httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://bridge"
    ) as client:
        health = await client.get("/healthz")
        ready = await client.get("/readyz")
    assert health.status_code == 200 and health.json()["status"] == "ok"
    assert ready.status_code == 200


async def test_protected_resource_metadata_is_public(fake_api_app):
    app = make_bridge_app(make_config(), httpx.ASGITransport(app=fake_api_app))
    async with app.router.lifespan_context(app), httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://bridge"
    ) as client:
        response = await client.get("/.well-known/oauth-protected-resource")
    assert response.status_code == 200
    assert response.json()["bearer_methods_supported"] == ["header"]


# --------------------------------------------------------------- failures
async def test_upstream_failure_returns_structured_error(fake_api, fake_api_app):
    fake_api.fail_mode = "500"
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_get_conversation", {})
    assert payload["ok"] is False
    assert payload["code"] == "upstream_error"


async def test_upstream_401_is_not_leaked_as_success(fake_api, fake_api_app):
    fake_api.fail_mode = "401"
    async with bridge_session(fake_api_app) as session:
        payload = await _call(session, "openhands_get_conversation", {})
    assert payload["ok"] is False
    assert payload["code"] == "upstream_unauthorized"


async def test_invalid_conversation_id_returns_structured_error(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(
            session, "openhands_get_conversation", {"conversation_id": "../../etc/passwd"}
        )
    assert payload["ok"] is False
    assert payload["code"] == "invalid_input"


async def test_unknown_conversation_id_returns_not_found(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        payload = await _call(
            session, "openhands_get_conversation", {"conversation_id": "a" * 32}
        )
    assert payload["ok"] is False
    assert payload["code"] == "not_found"


async def test_unexpected_tool_error_is_structured_not_raw(fake_api_app, monkeypatch):
    """An unexpected exception must not leak internals to the model."""

    from openhands_mcp_bridge import server as server_module

    async def exploding_call(self, name, arguments):
        raise RuntimeError("internal detail with sk-live-abcdef1234567890")

    monkeypatch.setattr(server_module.ToolService, "call", exploding_call)

    async with bridge_session(fake_api_app) as session:
        result = await session.call_tool("openhands_get_conversation", {})
    text = result.content[0].text
    assert result.isError is True
    assert "sk-live-abcdef1234567890" not in text
    assert "internal detail" not in text
    assert '"code": "upstream_error"' in text


async def test_invalid_limit_type_is_rejected(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        result = await session.call_tool("openhands_list_conversations", {"limit": "lots"})
    assert result.isError


async def test_unknown_tool_is_rejected(fake_api_app):
    async with bridge_session(fake_api_app) as session:
        result = await session.call_tool("openhands_delete_conversation", {})
    assert result.isError


async def test_rate_limiting_returns_429(fake_api_app):
    config = make_config(rate_limit_requests=2, rate_limit_window_seconds=60.0)
    app = make_bridge_app(config, httpx.ASGITransport(app=fake_api_app))
    headers = {
        "Authorization": f"Bearer {TEST_MCP_TOKEN}",
        "Accept": "application/json, text/event-stream",
    }
    async with app.router.lifespan_context(app), httpx.AsyncClient(
        transport=httpx.ASGITransport(app=app), base_url="http://bridge"
    ) as client:
        statuses = []
        for _ in range(4):
            response = await client.post(
                "/mcp",
                json={"jsonrpc": "2.0", "id": 1, "method": "tools/list"},
                headers=headers,
            )
            statuses.append(response.status_code)
    assert 429 in statuses
