"""Unit tests for the read-only OpenHands Cloud client."""

from __future__ import annotations

import httpx
import pytest

from openhands_mcp_bridge.errors import (
    InvalidInputError,
    NotFoundError,
    ResponseTooLargeError,
    UpstreamAuthError,
    UpstreamError,
)

from .conftest import TEST_API_KEY, TEST_CONVERSATION_ID, make_client, make_config


async def test_client_sends_bearer_auth(fake_api, fake_api_app):
    config = make_config()
    client = make_client(config, httpx.ASGITransport(app=fake_api_app))
    try:
        await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert fake_api.auth_header == f"Bearer {TEST_API_KEY}"


async def test_get_conversation_returns_record(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        record = await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert record["id"] == TEST_CONVERSATION_ID
    assert record["sandbox_status"] == "RUNNING"


async def test_only_get_requests_are_issued(fake_api, fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        await client.list_conversations(limit=5)
        await client.get_conversation(TEST_CONVERSATION_ID)
        await client.get_events(TEST_CONVERSATION_ID, limit=5)
        await client.count_events(TEST_CONVERSATION_ID)
        await client.download_trajectory(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert fake_api.requests, "expected the fake API to have been called"
    assert {r["method"] for r in fake_api.requests} == {"GET"}


async def test_list_conversations_pagination_fields(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        page = await client.list_conversations(limit=1)
    finally:
        await client.aclose()
    assert page["count"] == 1
    assert page["items"][0]["id"] == TEST_CONVERSATION_ID
    assert "next_page_id" in page


async def test_list_conversation_summary_drops_session_key(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        page = await client.list_conversations(limit=1)
    finally:
        await client.aclose()
    summary = page["items"][0]
    assert "session_api_key" not in summary
    assert "conversation_url" not in summary


async def test_get_events_excludes_full_state_by_default(fake_api, fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        await client.get_events(TEST_CONVERSATION_ID, limit=10)
    finally:
        await client.aclose()
    query = fake_api.requests[-1]["query"]
    assert query.get("exclude_full_state") == "true"


async def test_get_events_clamps_limit(fake_api, fake_api_app):
    config = make_config(max_event_page_limit=10)
    client = make_client(config, httpx.ASGITransport(app=fake_api_app))
    try:
        await client.get_events(TEST_CONVERSATION_ID, limit=10_000)
    finally:
        await client.aclose()
    assert int(fake_api.requests[-1]["query"]["limit"]) == 10


async def test_count_events(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        assert await client.count_events(TEST_CONVERSATION_ID) == 3
    finally:
        await client.aclose()


async def test_status_includes_event_count(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        status = await client.get_conversation_status(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert status["execution_status"] == "running"
    assert status["event_count"] == 3


async def test_trajectory_download_parses_zip(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        trajectory = await client.download_trajectory(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert trajectory["total_events"] == 3
    assert trajectory["returned_events"] == 3
    assert trajectory["conversation_id"] == TEST_CONVERSATION_ID


async def test_trajectory_meta_is_redacted(fake_api_app):
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        trajectory = await client.download_trajectory(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert trajectory["meta"]["agent"]["llm"]["api_key"] == "**********"


async def test_trajectory_size_cap(fake_api_app):
    config = make_config(max_trajectory_download_bytes=10)
    client = make_client(config, httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(ResponseTooLargeError):
            await client.download_trajectory(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_upstream_401_maps_to_auth_error(fake_api, fake_api_app):
    fake_api.fail_mode = "401"
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(UpstreamAuthError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_upstream_404_maps_to_not_found(fake_api, fake_api_app):
    fake_api.fail_mode = "404"
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(NotFoundError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_upstream_500_maps_to_upstream_error(fake_api, fake_api_app):
    fake_api.fail_mode = "500"
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(UpstreamError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_upstream_error_body_is_redacted(fake_api, fake_api_app):
    fake_api.fail_mode = "leaky_error"
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(UpstreamError) as excinfo:
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    body = excinfo.value.details.get("body", "")
    assert TEST_API_KEY not in body
    assert "sk-live-abcdef1234567890" not in body


async def test_malformed_json_raises_upstream_error(fake_api, fake_api_app):
    fake_api.fail_mode = "malformed"
    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(UpstreamError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


@pytest.mark.parametrize(
    "bad_id",
    ["", "   ", "../secrets", "abc/def", "abc?x=1", "a" * 100, "id with spaces"],
)
async def test_invalid_conversation_ids_are_rejected(bad_id):
    client = make_client(make_config(), httpx.MockTransport(lambda r: httpx.Response(200, json=[])))
    try:
        with pytest.raises(InvalidInputError):
            await client.get_conversation(bad_id)
    finally:
        await client.aclose()


async def test_timeout_raises_upstream_timeout():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("timed out", request=request)

    config = make_config(http_timeout=0.2, http_connect_timeout=0.2, http_max_retries=0)
    client = make_client(config, httpx.MockTransport(handler))
    try:
        from openhands_mcp_bridge.errors import UpstreamTimeoutError

        with pytest.raises(UpstreamTimeoutError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_network_error_is_retried_then_raises(fake_api_app):
    attempts = {"count": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        attempts["count"] += 1
        raise httpx.ConnectError("connection refused", request=request)

    config = make_config(http_max_retries=2)
    client = make_client(config, httpx.MockTransport(handler))
    try:
        with pytest.raises(UpstreamError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert attempts["count"] == 3


async def test_retries_transient_5xx_then_succeeds(fake_api_app):
    attempts = {"count": 0}

    def handler(request: httpx.Request) -> httpx.Response:
        attempts["count"] += 1
        if attempts["count"] == 1:
            return httpx.Response(503, json={"detail": "temporarily unavailable"})
        return httpx.Response(200, json=[{"id": TEST_CONVERSATION_ID}])

    config = make_config(http_max_retries=2)
    client = make_client(config, httpx.MockTransport(handler))
    try:
        record = await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
    assert record["id"] == TEST_CONVERSATION_ID
    assert attempts["count"] == 2


async def test_unknown_conversation_id_maps_to_not_found(fake_api_app):
    """OpenHands Cloud answers an unknown id with ``[null]``, not a 404."""

    client = make_client(make_config(), httpx.ASGITransport(app=fake_api_app))
    try:
        with pytest.raises(NotFoundError):
            await client.get_conversation("a" * 32)
    finally:
        await client.aclose()


async def test_null_only_record_list_is_not_treated_as_success():
    """A response of ``[null]`` must never be returned as a conversation."""

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=[None])

    client = make_client(make_config(), httpx.MockTransport(handler))
    try:
        with pytest.raises(NotFoundError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_empty_record_list_maps_to_not_found():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json=[])

    client = make_client(make_config(), httpx.MockTransport(handler))
    try:
        with pytest.raises(NotFoundError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()


async def test_non_list_lookup_payload_raises_upstream_error():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"unexpected": "shape"})

    client = make_client(make_config(), httpx.MockTransport(handler))
    try:
        with pytest.raises(UpstreamError):
            await client.get_conversation(TEST_CONVERSATION_ID)
    finally:
        await client.aclose()
