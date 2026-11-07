"""Shared pytest fixtures: a fake OpenHands Cloud API and a bridge ASGI app."""

from __future__ import annotations

import io
import json
import zipfile
from typing import Any

import pytest
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse, Response
from starlette.routing import Route

from openhands_mcp_bridge.client import OpenHandsClient
from openhands_mcp_bridge.config import Config
from openhands_mcp_bridge.server import create_app

TEST_API_KEY = "oh-cloud-test-key-do-not-use-in-production"
TEST_MCP_TOKEN = "mcp-endpoint-test-token-1234567890"
TEST_CONVERSATION_ID = "7177c0cd42934b7db0ec620706f3f547"

CONVERSATION_RECORD: dict[str, Any] = {
    "id": TEST_CONVERSATION_ID,
    "created_by_user_id": "user-1",
    "sandbox_id": "74IUPbcSmitpuimjX0u5di",
    "selected_repository": "owner/repo",
    "selected_branch": "main",
    "git_provider": "github",
    "title": "READ-ONLY ENGINEERING AUDIT",
    "trigger": "gui",
    "pr_number": [],
    "pull_requests": [],
    "additional_repositories": [],
    "llm_model": "openhands/deepseek-v4.1-flash",
    "metrics": {
        "accumulated_cost": 0.5,
        "accumulated_token_usage": {"prompt_tokens": 10, "completion_tokens": 2},
    },
    "parent_conversation_id": None,
    "sub_conversation_ids": [],
    "tags": {"repo_name": "owner/repo"},
    "created_at": "2026-11-07T07:45:58.419908Z",
    "updated_at": "2026-11-07T10:44:34.860458Z",
    "sandbox_status": "RUNNING",
    "execution_status": "running",
    "conversation_url": "https://sandbox.example/api/conversations/" + TEST_CONVERSATION_ID,
    "conversation_ui_url": "https://app.all-hands.dev/canvas/conversations/" + TEST_CONVERSATION_ID,
    "session_api_key": "super-secret-session-key",
}

EVENTS: list[dict[str, Any]] = [
    {
        "id": "e1e49d1a-5a97-4fe3-94ad-455384d3f441",
        "timestamp": "2026-11-07T07:45:57.441267",
        "source": "environment",
        "kind": "ConversationStateUpdateEvent",
        "key": "full_state",
        "value": {"agent": {"llm": {"api_key": "sk-leaked-should-be-redacted-000111"}}},
    },
    {
        "id": "6ce878db-bfcc-41c6-aabf-ffb49e74435e",
        "timestamp": "2026-11-07T07:46:00.000000",
        "source": "agent",
        "kind": "MessageEvent",
        "message": "hello",
    },
    {
        "id": "61871d74-26d3-44a5-830b-5bbf76638c1b",
        "timestamp": "2026-11-07T07:46:05.000000",
        "source": "agent",
        "kind": "ActionEvent",
        "tool_name": "terminal",
        "tool_call_id": "toolu_1",
        "action": {"command": "ls"},
    },
]


def _build_trajectory_zip() -> bytes:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr(
            "meta.json",
            json.dumps(
                {
                    "conversation_id": TEST_CONVERSATION_ID,
                    "agent": {"llm": {"api_key": "sk-in-trajectory-should-be-redacted"}},
                }
            ),
        )
        for index, event in enumerate(EVENTS):
            archive.writestr(f"event_{index:06d}_{event['id']}.json", json.dumps(event))
    return buffer.getvalue()


TRAJECTORY_ZIP = _build_trajectory_zip()


class FakeOpenHandsAPI:
    """Minimal stand-in for OpenHands Cloud's v1 API."""

    def __init__(self) -> None:
        self.requests: list[dict[str, Any]] = []
        self.auth_header: str | None = None
        self.fail_mode: str | None = None
        self.events = list(EVENTS)
        self.conversations = [CONVERSATION_RECORD]
        self.event_pages = 1

    async def dispatch(self, request: Request) -> Response:
        self.requests.append(
            {
                "method": request.method,
                "path": request.url.path,
                "query": dict(request.query_params),
            }
        )
        self.auth_header = request.headers.get("authorization")

        if self.fail_mode == "timeout":
            import asyncio

            await asyncio.sleep(5)
        if self.fail_mode == "401":
            return JSONResponse({"detail": "unauthorized"}, status_code=401)
        if self.fail_mode == "500":
            return JSONResponse({"detail": "boom"}, status_code=500)
        if self.fail_mode == "404":
            return JSONResponse({"detail": "not found"}, status_code=404)
        if self.fail_mode == "malformed":
            return Response("not json", media_type="application/json", status_code=200)
        if self.fail_mode == "leaky_error":
            return Response(
                f"upstream exploded with key {TEST_API_KEY} and sk-live-abcdef1234567890",
                status_code=500,
            )

        path = request.url.path
        if path.endswith("/app-conversations/search"):
            limit = int(request.query_params.get("limit", 100))
            return JSONResponse(
                {"items": self.conversations[:limit], "next_page_id": None}
            )
        if path.endswith("/app-conversations"):
            requested = request.query_params.get("ids")
            if requested:
                # OpenHands Cloud answers an unknown id with [null], not a 404.
                match = [c for c in self.conversations if c.get("id") == requested]
                return JSONResponse(match or [None])
            return JSONResponse(self.conversations)
        if path.endswith("/events/count"):
            return JSONResponse({"count": len(self.events)})
        if path.endswith("/events/search"):
            limit = int(request.query_params.get("limit", 100))
            page = self.events[:limit]
            return JSONResponse({"items": page, "next_page_id": None})
        if path.endswith("/download"):
            return Response(TRAJECTORY_ZIP, media_type="application/zip")
        return JSONResponse({"detail": "unhandled"}, status_code=404)


@pytest.fixture()
def fake_api() -> FakeOpenHandsAPI:
    return FakeOpenHandsAPI()


@pytest.fixture()
def fake_api_app(fake_api: FakeOpenHandsAPI) -> Starlette:
    routes = [
        Route("/{path:path}", fake_api.dispatch, methods=["GET"]),
    ]
    return Starlette(routes=routes)


def make_config(**overrides: Any) -> Config:
    values: dict[str, Any] = {
        "openhands_api_key": TEST_API_KEY,
        "openhands_base_url": "https://app.all-hands.dev",
        "openhands_api_prefix": "/api/v1",
        "mcp_auth_tokens": [TEST_MCP_TOKEN],
        "require_auth": True,
        "http_timeout": 2.0,
        "http_connect_timeout": 1.0,
        "http_max_retries": 0,
        "rate_limit_requests": 1000,
        "rate_limit_window_seconds": 60.0,
    }
    values.update(overrides)
    return Config(**values)


def make_client(config: Config, transport: Any) -> OpenHandsClient:
    import httpx

    http_client = httpx.AsyncClient(
        transport=transport,
        base_url=config.api_base,
        timeout=httpx.Timeout(config.http_timeout, connect=config.http_connect_timeout),
    )
    return OpenHandsClient(config, http_client=http_client)


def make_bridge_app(config: Config, transport: Any) -> Starlette:
    client = make_client(config, transport)
    return create_app(config, client=client)
