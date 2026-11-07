"""The five read-only MCP tools exposed by the bridge.

Each tool:

* is declared with an explicit JSON Schema (``TOOL_SPECS``),
* validates its arguments with a pydantic model,
* calls the read-only :class:`~openhands_mcp_bridge.client.OpenHandsClient`,
* returns a JSON-serialisable dict with secrets redacted.

There is intentionally no tool that can send messages, execute commands,
modify files or create conversations.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any

from pydantic import BaseModel, Field, ValidationError

from .client import OpenHandsClient
from .config import DEFAULT_CONVERSATION_ID
from .errors import BridgeError, InvalidInputError
from .redaction import redact

logger = logging.getLogger(__name__)

CONVERSATION_ID_FIELD = Field(
    default=DEFAULT_CONVERSATION_ID,
    description=(
        "OpenHands Cloud conversation id. Defaults to the configured conversation "
        f"({DEFAULT_CONVERSATION_ID})."
    ),
    max_length=64,
)


class ListConversationsArgs(BaseModel):
    limit: int = Field(20, ge=1, le=100, description="Max conversations per page.")
    page_id: str | None = Field(None, description="Opaque page id from a previous call.")
    max_pages: int = Field(1, ge=1, le=10, description="How many pages to follow.")
    title_contains: str | None = Field(None, description="Filter: title contains text.")
    sandbox_id: str | None = Field(None, description="Filter: exact sandbox id.")
    include_sub_conversations: bool = Field(
        False, description="Include sub-conversations in the listing."
    )
    created_at_gte: str | None = Field(None, description="Filter: created_at >= ISO-8601.")
    created_at_lt: str | None = Field(None, description="Filter: created_at < ISO-8601.")
    updated_at_gte: str | None = Field(None, description="Filter: updated_at >= ISO-8601.")
    updated_at_lt: str | None = Field(None, description="Filter: updated_at < ISO-8601.")


class GetConversationArgs(BaseModel):
    conversation_id: str = CONVERSATION_ID_FIELD


class GetConversationStatusArgs(BaseModel):
    conversation_id: str = CONVERSATION_ID_FIELD


class GetConversationEventsArgs(BaseModel):
    conversation_id: str = CONVERSATION_ID_FIELD
    limit: int = Field(50, ge=1, le=100, description="Max events per page.")
    page_id: str | None = Field(None, description="Opaque page id from a previous call.")
    max_pages: int = Field(1, ge=1, le=20, description="How many pages to follow.")
    max_events: int | None = Field(
        None, ge=1, le=1000, description="Hard cap on events returned in this call."
    )
    sort_order: str | None = Field(
        None, description="TIMESTAMP (oldest first) or TIMESTAMP_DESC (newest first)."
    )
    kind: str | None = Field(None, description="Filter by event kind, e.g. MessageEvent.")
    timestamp_gte: str | None = Field(None, description="Filter: timestamp >= ISO-8601.")
    timestamp_lt: str | None = Field(None, description="Filter: timestamp < ISO-8601.")
    exclude_full_state: bool = Field(
        True, description="Omit large full-state snapshots (recommended)."
    )


class DownloadTrajectoryArgs(BaseModel):
    conversation_id: str = CONVERSATION_ID_FIELD
    include_meta: bool = Field(True, description="Include the archive's meta.json content.")
    max_events: int | None = Field(
        None, ge=1, le=500, description="Max events to return from the archive."
    )


#: Canonical argument models per tool. ``server.py`` registers thin functions
#: whose signatures mirror these models; ``tests`` assert the two stay aligned.
TOOL_MODELS: dict[str, type[BaseModel]] = {
    "openhands_list_conversations": ListConversationsArgs,
    "openhands_get_conversation": GetConversationArgs,
    "openhands_get_conversation_status": GetConversationStatusArgs,
    "openhands_get_conversation_events": GetConversationEventsArgs,
    "openhands_download_trajectory": DownloadTrajectoryArgs,
}

TOOL_DESCRIPTIONS: dict[str, str] = {
    "openhands_list_conversations": (
        "List recent OpenHands Cloud conversations (read-only). Supports pagination "
        "and filtering by title, sandbox id and timestamps."
    ),
    "openhands_get_conversation": (
        "Fetch the full metadata record for a single OpenHands Cloud conversation by id "
        "(read-only)."
    ),
    "openhands_get_conversation_status": (
        "Return a compact status summary for a conversation: sandbox status, execution "
        "status, timestamps and event count (read-only)."
    ),
    "openhands_get_conversation_events": (
        "Read conversation events (messages, actions, observations) with pagination, "
        "sorting and filtering (read-only)."
    ),
    "openhands_download_trajectory": (
        "Download and parse a conversation's trajectory archive (meta.json + events) "
        "directly from OpenHands Cloud (read-only)."
    ),
}

TOOL_NAMES: list[str] = list(TOOL_MODELS)


def tool_input_schema(name: str) -> dict[str, Any]:
    """JSON Schema for a tool's arguments (derived from its pydantic model)."""

    return TOOL_MODELS[name].model_json_schema()


@dataclass
class ToolService:
    """Thin, testable layer that binds the tool schemas to the API client."""

    client: OpenHandsClient

    async def call(self, name: str, arguments: dict[str, Any] | None) -> dict[str, Any]:
        arguments = arguments or {}
        handler = {
            "openhands_list_conversations": self.list_conversations,
            "openhands_get_conversation": self.get_conversation,
            "openhands_get_conversation_status": self.get_conversation_status,
            "openhands_get_conversation_events": self.get_conversation_events,
            "openhands_download_trajectory": self.download_trajectory,
        }.get(name)
        if handler is None:
            raise InvalidInputError(f"Unknown tool: {name}")

        try:
            return await handler(arguments)
        except ValidationError as exc:
            raise InvalidInputError(
                "Invalid tool arguments.", details={"errors": exc.errors(include_url=False)}
            ) from exc

    async def list_conversations(self, arguments: dict[str, Any]) -> dict[str, Any]:
        args = ListConversationsArgs(**arguments)
        result = await self.client.list_conversations(
            limit=args.limit,
            page_id=args.page_id,
            title_contains=args.title_contains,
            sandbox_id=args.sandbox_id,
            created_at_gte=args.created_at_gte,
            created_at_lt=args.created_at_lt,
            updated_at_gte=args.updated_at_gte,
            updated_at_lt=args.updated_at_lt,
            include_sub_conversations=args.include_sub_conversations,
            max_pages=args.max_pages,
        )
        return _ok("openhands_list_conversations", redact(result))

    async def get_conversation(self, arguments: dict[str, Any]) -> dict[str, Any]:
        args = GetConversationArgs(**arguments)
        record = await self.client.get_conversation(args.conversation_id)
        summary = self.client._summarize_conversation(record)
        return _ok(
            "openhands_get_conversation",
            {
                "conversation": redact(summary),
                "raw": redact(record),
            },
        )

    async def get_conversation_status(self, arguments: dict[str, Any]) -> dict[str, Any]:
        args = GetConversationStatusArgs(**arguments)
        status = await self.client.get_conversation_status(args.conversation_id)
        return _ok("openhands_get_conversation_status", redact(status))

    async def get_conversation_events(self, arguments: dict[str, Any]) -> dict[str, Any]:
        args = GetConversationEventsArgs(**arguments)
        result = await self.client.get_events(
            args.conversation_id,
            limit=args.limit,
            page_id=args.page_id,
            sort_order=args.sort_order,
            kind_eq=args.kind,
            timestamp_gte=args.timestamp_gte,
            timestamp_lt=args.timestamp_lt,
            exclude_full_state=args.exclude_full_state,
            max_events=args.max_events,
            max_pages=args.max_pages,
        )
        return _ok("openhands_get_conversation_events", redact(result))

    async def download_trajectory(self, arguments: dict[str, Any]) -> dict[str, Any]:
        args = DownloadTrajectoryArgs(**arguments)
        result = await self.client.download_trajectory(args.conversation_id)
        if not args.include_meta:
            result.pop("meta", None)
        if args.max_events and len(result.get("events", [])) > args.max_events:
            result["events"] = result["events"][: args.max_events]
            result["returned_events"] = len(result["events"])
            result["truncated"] = True
        return _ok("openhands_download_trajectory", redact(result))


def _ok(tool: str, data: dict[str, Any]) -> dict[str, Any]:
    return {"ok": True, "tool": tool, **data}


def error_payload(tool: str, error: BridgeError) -> dict[str, Any]:
    payload = error.to_dict()
    payload["tool"] = tool
    payload["ok"] = False
    return redact(payload)
