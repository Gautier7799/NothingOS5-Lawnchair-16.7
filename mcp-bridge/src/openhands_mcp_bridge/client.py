"""Read-only OpenHands Cloud REST API v1 client.

Design notes
------------
* Only ``GET`` requests are ever issued. There is no code path in this module
  that can send a message, execute a command, modify a file or create a
  conversation.
* Every request carries ``Authorization: Bearer <OPENHANDS_CLOUD_API_KEY>``.
  The key is held in memory only and is passed through
  :func:`openhands_mcp_bridge.redaction.redact_text` before any error text is
  surfaced.
* Responses are validated (JSON shape, expected keys) and bounded in size.
* Transient failures (timeouts, connection resets, HTTP 429/5xx) are retried
  with exponential backoff + jitter. HTTP 401/403 are not retried.
"""

from __future__ import annotations

import asyncio
import io
import json
import logging
import random
import zipfile
from collections.abc import Iterable
from typing import Any
from urllib.parse import quote

import httpx

from .config import Config
from .errors import (
    InvalidInputError,
    NotFoundError,
    ResponseTooLargeError,
    UpstreamAuthError,
    UpstreamError,
    UpstreamTimeoutError,
)
from .rate_limit import RateLimiter
from .redaction import redact, redact_text

logger = logging.getLogger(__name__)

_RETRYABLE_STATUS = {429, 500, 502, 503, 504}
_CONVERSATION_ID_MAX_LEN = 64


class OpenHandsClient:
    """Async, read-only client for the OpenHands Cloud v1 API."""

    def __init__(
        self,
        config: Config,
        *,
        http_client: httpx.AsyncClient | None = None,
        upstream_limiter: RateLimiter | None = None,
    ) -> None:
        self._config = config
        self._api_key = config.openhands_api_key
        self._owns_client = http_client is None
        self._client = http_client or httpx.AsyncClient(
            base_url=config.api_base,
            timeout=httpx.Timeout(
                config.http_timeout, connect=config.http_connect_timeout
            ),
            follow_redirects=False,
            headers={
                "Accept": "application/json",
                "User-Agent": "openhands-mcp-bridge/1.0",
            },
        )
        self._limiter = upstream_limiter or RateLimiter(
            capacity=max(config.rate_limit_requests, 1),
            window_seconds=config.rate_limit_window_seconds,
        )

    # ------------------------------------------------------------------ util
    def _headers(self) -> dict[str, str]:
        return {"Authorization": f"Bearer {self._api_key}"}

    def _safe(self, text: str) -> str:
        return redact_text(text, extra_secrets=[self._api_key])

    async def _sleep_for_retry(self, attempt: int, retry_after: str | None = None) -> None:
        if retry_after:
            try:
                await asyncio.sleep(min(float(retry_after), 30.0))
                return
            except ValueError:
                pass
        # Jitter only needs to decorrelate retries; it is not security-relevant.
        backoff = min(2.0**attempt, 8.0) + random.uniform(0, 0.5)  # noqa: S311
        await asyncio.sleep(backoff)

    async def _get_json(
        self,
        path: str,
        *,
        params: Iterable[tuple[str, Any]] | None = None,
        max_bytes: int | None = None,
    ) -> Any:
        """GET ``path`` and return decoded JSON, with retries and validation."""

        await self._enforce_upstream_rate_limit()
        max_bytes = max_bytes or self._config.max_response_bytes
        last_error: Exception | None = None

        for attempt in range(self._config.http_max_retries + 1):
            try:
                response = await self._client.get(
                    path, params=params, headers=self._headers()
                )
            except httpx.TimeoutException as exc:
                last_error = UpstreamTimeoutError(
                    "OpenHands Cloud did not respond in time."
                )
                if attempt < self._config.http_max_retries:
                    await self._sleep_for_retry(attempt)
                    continue
                raise last_error from exc
            except httpx.HTTPError as exc:
                last_error = UpstreamError(
                    f"Network error contacting OpenHands Cloud: {type(exc).__name__}"
                )
                if attempt < self._config.http_max_retries:
                    await self._sleep_for_retry(attempt)
                    continue
                raise last_error from exc

            if (
                response.status_code in _RETRYABLE_STATUS
                and attempt < self._config.http_max_retries
            ):
                await self._sleep_for_retry(attempt, response.headers.get("Retry-After"))
                continue

            return self._decode(response, max_bytes=max_bytes)

        raise last_error or UpstreamError("Unexpected upstream failure.")

    async def _enforce_upstream_rate_limit(self) -> None:
        allowed, retry_after = await self._limiter.check("openhands-upstream")
        if not allowed:
            raise UpstreamError(
                "Upstream rate limit reached; retry shortly.",
                details={"retry_after_seconds": round(retry_after, 2)},
            )

    def _decode(self, response: httpx.Response, *, max_bytes: int) -> Any:
        if len(response.content) > max_bytes:
            raise ResponseTooLargeError(
                "Upstream response exceeded the configured size limit.",
                details={"limit_bytes": max_bytes},
            )

        status = response.status_code
        if status == 200:
            try:
                return response.json()
            except json.JSONDecodeError as exc:
                raise UpstreamError("OpenHands Cloud returned malformed JSON.") from exc
        if status in (401, 403):
            raise UpstreamAuthError(
                f"OpenHands Cloud rejected the API key (HTTP {status})."
            )
        if status == 404:
            raise NotFoundError("The requested OpenHands resource was not found.")
        if status == 422:
            raise InvalidInputError("OpenHands Cloud rejected the request parameters.")
        if status == 429:
            raise UpstreamError(
                "OpenHands Cloud rate limit exceeded.",
                details={"retry_after": response.headers.get("Retry-After")},
            )
        snippet = self._safe(response.text[:400]) if response.text else ""
        raise UpstreamError(
            f"OpenHands Cloud returned HTTP {status}.",
            details={"upstream_status": status, "body": snippet},
        )

    # --------------------------------------------------------------- helpers
    @staticmethod
    def _validate_conversation_id(conversation_id: str) -> str:
        if not isinstance(conversation_id, str):
            raise InvalidInputError("conversation_id must be a string.")
        candidate = conversation_id.strip()
        if not candidate:
            raise InvalidInputError("conversation_id must not be empty.")
        if len(candidate) > _CONVERSATION_ID_MAX_LEN:
            raise InvalidInputError("conversation_id is too long.")
        # OpenHands ids are hex/uuid-like; reject anything with path or query
        # metacharacters so the value can never escape the intended endpoint.
        if not all(ch.isalnum() or ch in "-_" for ch in candidate):
            raise InvalidInputError(
                "conversation_id may only contain letters, digits, '-' and '_'."
            )
        return candidate

    def _clamp(self, value: int | None, *, default: int, maximum: int) -> int:
        if value is None:
            return default
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            raise InvalidInputError("limit must be an integer.") from None
        if parsed <= 0:
            raise InvalidInputError("limit must be a positive integer.")
        return min(parsed, maximum)

    # --------------------------------------------------------------- public
    async def list_conversations(
        self,
        *,
        limit: int | None = None,
        page_id: str | None = None,
        title_contains: str | None = None,
        sandbox_id: str | None = None,
        created_at_gte: str | None = None,
        created_at_lt: str | None = None,
        updated_at_gte: str | None = None,
        updated_at_lt: str | None = None,
        include_sub_conversations: bool = False,
        max_pages: int = 1,
    ) -> dict[str, Any]:
        """List conversations using ``GET /api/v1/app-conversations/search``."""

        page_limit = self._clamp(
            limit, default=20, maximum=self._config.max_list_page_limit
        )
        max_pages = max(1, min(int(max_pages or 1), 10))

        items: list[dict] = []
        next_page_id = page_id
        pages_fetched = 0

        while pages_fetched < max_pages:
            params: list[tuple[str, Any]] = [("limit", page_limit)]
            if next_page_id:
                params.append(("page_id", next_page_id))
            if title_contains:
                params.append(("title__contains", title_contains))
            if sandbox_id:
                params.append(("sandbox_id__eq", sandbox_id))
            if created_at_gte:
                params.append(("created_at__gte", created_at_gte))
            if created_at_lt:
                params.append(("created_at__lt", created_at_lt))
            if updated_at_gte:
                params.append(("updated_at__gte", updated_at_gte))
            if updated_at_lt:
                params.append(("updated_at__lt", updated_at_lt))
            if include_sub_conversations:
                params.append(("include_sub_conversations", "true"))

            payload = await self._get_json("/app-conversations/search", params=params)
            page_items, next_page_id = self._parse_page(payload, "app-conversations")
            items.extend(page_items)
            pages_fetched += 1
            if not next_page_id:
                break

        return {
            "items": [self._summarize_conversation(item) for item in items],
            "count": len(items),
            "next_page_id": next_page_id,
            "pages_fetched": pages_fetched,
        }

    async def get_conversation(self, conversation_id: str) -> dict[str, Any]:
        """Fetch one conversation via ``GET /api/v1/app-conversations?ids=``.

        OpenHands Cloud answers an unknown id with ``[null]`` (or a list of
        non-matching records) rather than a 404, so an empty or null-only
        response must be surfaced as :class:`NotFoundError` instead of being
        treated as a successful lookup.
        """

        cid = self._validate_conversation_id(conversation_id)
        payload = await self._get_json("/app-conversations", params=[("ids", cid)])
        if isinstance(payload, list):
            records = payload
        elif isinstance(payload, dict) and "items" in payload:
            records = payload["items"]
        else:
            raise UpstreamError("Unexpected response shape for conversation lookup.")
        if not isinstance(records, list):
            raise UpstreamError("Unexpected response shape for conversation lookup.")

        candidates = [record for record in records if isinstance(record, dict)]
        for record in candidates:
            if record.get("id") == cid:
                return record
        if candidates:
            return candidates[0]
        raise NotFoundError(f"Conversation {cid} was not found.")

    async def get_conversation_status(self, conversation_id: str) -> dict[str, Any]:
        record = await self.get_conversation(conversation_id)
        event_count = None
        try:
            event_count = await self.count_events(conversation_id)
        except Exception as exc:  # noqa: BLE001 - status is best-effort
            logger.info("event count unavailable: %s", type(exc).__name__)
        return {
            "id": record.get("id"),
            "sandbox_id": record.get("sandbox_id"),
            "title": record.get("title"),
            "sandbox_status": record.get("sandbox_status"),
            "execution_status": record.get("execution_status"),
            "created_at": record.get("created_at"),
            "updated_at": record.get("updated_at"),
            "conversation_ui_url": record.get("conversation_ui_url"),
            "event_count": event_count,
        }

    async def count_events(self, conversation_id: str) -> int:
        cid = self._validate_conversation_id(conversation_id)
        payload = await self._get_json(f"/conversation/{quote(cid)}/events/count")
        if isinstance(payload, dict) and "count" in payload:
            return int(payload["count"])
        if isinstance(payload, int):
            return payload
        raise UpstreamError("Unexpected response shape for event count.")

    async def get_events(
        self,
        conversation_id: str,
        *,
        limit: int | None = None,
        page_id: str | None = None,
        sort_order: str | None = None,
        kind_eq: str | None = None,
        timestamp_gte: str | None = None,
        timestamp_lt: str | None = None,
        exclude_full_state: bool = True,
        max_events: int | None = None,
        max_pages: int = 1,
    ) -> dict[str, Any]:
        """Read events via ``GET /api/v1/conversation/{id}/events/search``."""

        cid = self._validate_conversation_id(conversation_id)
        page_limit = self._clamp(
            limit, default=50, maximum=self._config.max_event_page_limit
        )
        cap = min(
            int(max_events or self._config.max_events_returned),
            self._config.max_events_returned,
        )
        if sort_order and sort_order not in {"TIMESTAMP", "TIMESTAMP_DESC"}:
            raise InvalidInputError("sort_order must be TIMESTAMP or TIMESTAMP_DESC.")
        max_pages = max(1, min(int(max_pages or 1), 20))

        items: list[dict] = []
        next_page_id = page_id
        pages_fetched = 0
        truncated = False

        while pages_fetched < max_pages and len(items) < cap:
            remaining = cap - len(items)
            params: list[tuple[str, Any]] = [("limit", min(page_limit, remaining))]
            if next_page_id:
                params.append(("page_id", next_page_id))
            if sort_order:
                params.append(("sort_order", sort_order))
            if kind_eq:
                params.append(("kind__eq", kind_eq))
            if timestamp_gte:
                params.append(("timestamp__gte", timestamp_gte))
            if timestamp_lt:
                params.append(("timestamp__lt", timestamp_lt))
            if exclude_full_state:
                params.append(("exclude_full_state", "true"))

            payload = await self._get_json(
                f"/conversation/{quote(cid)}/events/search", params=params
            )
            page_items, next_page_id = self._parse_page(payload, "events")
            items.extend(page_items)
            pages_fetched += 1
            if not next_page_id:
                break
        else:
            truncated = bool(next_page_id) or len(items) >= cap

        if len(items) > cap:
            items = items[:cap]
            truncated = True

        return {
            "items": items,
            "count": len(items),
            "next_page_id": next_page_id,
            "pages_fetched": pages_fetched,
            "truncated": truncated,
        }

    async def download_trajectory(self, conversation_id: str) -> dict[str, Any]:
        """Download + parse the trajectory archive.

        ``GET /api/v1/app-conversations/{id}/download`` returns a ZIP file. We
        stream it, enforce a hard size cap, then parse the ``meta.json`` plus
        ``event_*.json`` members in memory.
        """

        cid = self._validate_conversation_id(conversation_id)
        await self._enforce_upstream_rate_limit()

        path = f"/app-conversations/{quote(cid)}/download"
        cap = self._config.max_trajectory_download_bytes
        buffer = bytearray()

        try:
            async with self._client.stream(
                "GET", path, headers=self._headers()
            ) as response:
                if response.status_code != 200:
                    await response.aread()
                    self._decode(response, max_bytes=self._config.max_response_bytes)
                async for chunk in response.aiter_bytes():
                    buffer.extend(chunk)
                    if len(buffer) > cap:
                        raise ResponseTooLargeError(
                            "Trajectory archive exceeded the configured size limit.",
                            details={"limit_bytes": cap},
                        )
        except httpx.TimeoutException as exc:
            raise UpstreamTimeoutError(
                "Timed out while downloading the trajectory."
            ) from exc
        except httpx.HTTPError as exc:
            raise UpstreamError(
                f"Network error while downloading the trajectory: {type(exc).__name__}"
            ) from exc

        return self._parse_trajectory(bytes(buffer))

    # ------------------------------------------------------------- internals
    def _parse_page(self, payload: Any, what: str) -> tuple[list[dict], str | None]:
        if not isinstance(payload, dict) or "items" not in payload:
            raise UpstreamError(f"Unexpected response shape while listing {what}.")
        items = payload.get("items")
        if not isinstance(items, list):
            raise UpstreamError(f"Unexpected 'items' type while listing {what}.")
        next_page_id = payload.get("next_page_id")
        if next_page_id is not None and not isinstance(next_page_id, str):
            next_page_id = None
        return [item for item in items if isinstance(item, dict)], next_page_id

    def _summarize_conversation(self, record: dict) -> dict:
        """Project a conversation record onto the fields useful to a model.

        ``conversation_url`` and ``session_api_key`` are deliberately dropped:
        they grant direct sandbox access and are never needed to *read* a
        conversation.
        """

        metrics = record.get("metrics") or {}
        token_usage = (metrics or {}).get("accumulated_token_usage") or {}
        return {
            "id": record.get("id"),
            "title": record.get("title"),
            "sandbox_id": record.get("sandbox_id"),
            "sandbox_status": record.get("sandbox_status"),
            "execution_status": record.get("execution_status"),
            "selected_repository": record.get("selected_repository"),
            "selected_branch": record.get("selected_branch"),
            "git_provider": record.get("git_provider"),
            "llm_model": record.get("llm_model"),
            "created_at": record.get("created_at"),
            "updated_at": record.get("updated_at"),
            "conversation_ui_url": record.get("conversation_ui_url"),
            "pr_number": record.get("pr_number"),
            "parent_conversation_id": record.get("parent_conversation_id"),
            "sub_conversation_ids": record.get("sub_conversation_ids"),
            "accumulated_cost": (metrics or {}).get("accumulated_cost"),
            "accumulated_token_usage": {
                "prompt_tokens": token_usage.get("prompt_tokens"),
                "completion_tokens": token_usage.get("completion_tokens"),
            }
            if token_usage
            else None,
        }

    def _parse_trajectory(self, raw: bytes) -> dict[str, Any]:
        try:
            archive = zipfile.ZipFile(io.BytesIO(raw))
        except zipfile.BadZipFile as exc:
            raise UpstreamError("Trajectory download was not a valid ZIP archive.") from exc

        names = sorted(archive.namelist())
        meta: Any = None
        event_names = [n for n in names if n.startswith("event_") and n.endswith(".json")]
        event_names.sort()

        total_events = len(event_names)
        cap = self._config.max_trajectory_events_returned
        selected = event_names[:cap]

        events: list[dict] = []
        skipped = 0
        for name in selected:
            try:
                data = archive.read(name)
            except KeyError:
                skipped += 1
                continue
            if len(data) > self._config.max_trajectory_event_bytes:
                skipped += 1
                continue
            try:
                events.append(json.loads(data.decode("utf-8")))
            except (UnicodeDecodeError, json.JSONDecodeError):
                skipped += 1

        if "meta.json" in names:
            try:
                meta = json.loads(archive.read("meta.json").decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError):
                meta = None

        archive.close()

        meta_id = (meta or {}).get("conversation_id") if isinstance(meta, dict) else None
        return {
            "conversation_id": meta_id,
            "meta": redact(meta) if meta is not None else None,
            "events": events,
            "total_events": total_events,
            "returned_events": len(events),
            "skipped_events": skipped,
            "truncated": total_events > len(events),
            "archive_bytes": len(raw),
            "files": names,
        }

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()
