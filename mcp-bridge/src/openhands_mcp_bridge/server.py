"""MCP server wiring: FastMCP instance, auth/rate-limit middleware, ASGI app.

Transport
---------
The bridge exposes the MCP **Streamable HTTP** transport (``POST``/``GET``/``DELETE``
on ``MCP_PATH``, default ``/mcp``). That is the transport ChatGPT's connector
"remote MCP server" support speaks.

Security
--------
* A raw ASGI middleware (:class:`EndpointSecurityMiddleware`) runs *before* the
  MCP handler. It enforces bearer-token authentication and a per-principal rate
  limit, and is the single place that can let an unauthenticated request reach
  the tool handlers.
* ``/healthz`` and ``/readyz`` are the only unauthenticated endpoints (plus the
  OAuth discovery documents required for the authorization flow).
* No secrets are logged; failed authentication is logged without the token.
"""

from __future__ import annotations

import json
import logging
from typing import Any

from mcp.server.fastmcp import FastMCP
from mcp.server.fastmcp.exceptions import ToolError
from pydantic import BaseModel
from starlette.applications import Starlette
from starlette.requests import Request
from starlette.responses import JSONResponse, Response

from .auth import Authenticator, OAuthJWTAuthenticator
from .client import OpenHandsClient
from .config import DEFAULT_CONVERSATION_ID, Config, load_config
from .errors import BridgeError, UpstreamError
from .rate_limit import RateLimiter
from .tools import ToolService, error_payload

logger = logging.getLogger(__name__)

SERVER_NAME = "openhands-cloud-mcp-bridge"
SERVER_INSTRUCTIONS = (
    "Read-only access to OpenHands Cloud. Use these tools to list conversations, "
    "inspect a conversation, read its events and download its trajectory. No tool can "
    "modify anything in OpenHands."
)

#: Paths served without authentication.
_PUBLIC_PATHS = frozenset(
    {
        "/healthz",
        "/readyz",
        "/.well-known/oauth-protected-resource",
        "/.well-known/oauth-authorization-server",
        "/.well-known/openid-configuration",
    }
)


def _schema_without_title(model: type[BaseModel]) -> dict[str, Any]:
    schema = model.model_json_schema()
    schema.pop("title", None)
    return schema


def build_server(config: Config, service: ToolService) -> FastMCP:
    """Create the :class:`FastMCP` instance and register the five tools."""

    server = FastMCP(
        name=SERVER_NAME,
        instructions=SERVER_INSTRUCTIONS,
        host=config.host,
        port=config.port,
        streamable_http_path=config.mount_path,
        stateless_http=True,
        json_response=False,
        log_level=config.log_level,
    )

    async def _dispatch(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        try:
            return await service.call(name, arguments)
        except BridgeError as exc:
            logger.info("tool %s failed: %s", name, exc.code)
            # Surface a structured, secret-free payload as an MCP tool error so
            # the model can react to ``code`` without seeing raw upstream text.
            raise ToolError(json.dumps(error_payload(name, exc))) from None
        except Exception as exc:
            # An unexpected failure must still be a structured, non-revealing
            # tool error: FastMCP would otherwise stringify the exception and
            # expose internal details.
            logger.exception("tool %s raised an unexpected error", name)
            raise ToolError(
                json.dumps(
                    error_payload(name, UpstreamError("The bridge hit an internal error."))
                )
            ) from exc

    # --- openhands_list_conversations -----------------------------------
    async def openhands_list_conversations(
        limit: int = 20,
        page_id: str | None = None,
        max_pages: int = 1,
        title_contains: str | None = None,
        sandbox_id: str | None = None,
        include_sub_conversations: bool = False,
        created_at_gte: str | None = None,
        created_at_lt: str | None = None,
        updated_at_gte: str | None = None,
        updated_at_lt: str | None = None,
    ) -> dict[str, Any]:
        """List recent OpenHands Cloud conversations (read-only)."""
        return await _dispatch(
            "openhands_list_conversations",
            {
                "limit": limit,
                "page_id": page_id,
                "max_pages": max_pages,
                "title_contains": title_contains,
                "sandbox_id": sandbox_id,
                "include_sub_conversations": include_sub_conversations,
                "created_at_gte": created_at_gte,
                "created_at_lt": created_at_lt,
                "updated_at_gte": updated_at_gte,
                "updated_at_lt": updated_at_lt,
            },
        )

    # --- openhands_get_conversation -------------------------------------
    async def openhands_get_conversation(
        conversation_id: str = DEFAULT_CONVERSATION_ID,
    ) -> dict[str, Any]:
        """Fetch the full metadata record for a conversation (read-only)."""
        return await _dispatch("openhands_get_conversation", {"conversation_id": conversation_id})

    # --- openhands_get_conversation_status ------------------------------
    async def openhands_get_conversation_status(
        conversation_id: str = DEFAULT_CONVERSATION_ID,
    ) -> dict[str, Any]:
        """Return a compact status summary for a conversation (read-only)."""
        return await _dispatch(
            "openhands_get_conversation_status", {"conversation_id": conversation_id}
        )

    # --- openhands_get_conversation_events ------------------------------
    async def openhands_get_conversation_events(
        conversation_id: str = DEFAULT_CONVERSATION_ID,
        limit: int = 50,
        page_id: str | None = None,
        max_pages: int = 1,
        max_events: int | None = None,
        sort_order: str | None = None,
        kind: str | None = None,
        timestamp_gte: str | None = None,
        timestamp_lt: str | None = None,
        exclude_full_state: bool = True,
    ) -> dict[str, Any]:
        """Read conversation events with pagination, sorting and filtering (read-only)."""
        return await _dispatch(
            "openhands_get_conversation_events",
            {
                "conversation_id": conversation_id,
                "limit": limit,
                "page_id": page_id,
                "max_pages": max_pages,
                "max_events": max_events,
                "sort_order": sort_order,
                "kind": kind,
                "timestamp_gte": timestamp_gte,
                "timestamp_lt": timestamp_lt,
                "exclude_full_state": exclude_full_state,
            },
        )

    # --- openhands_download_trajectory ----------------------------------
    async def openhands_download_trajectory(
        conversation_id: str = DEFAULT_CONVERSATION_ID,
        include_meta: bool = True,
        max_events: int | None = None,
    ) -> dict[str, Any]:
        """Download and parse a conversation's trajectory archive (read-only)."""
        return await _dispatch(
            "openhands_download_trajectory",
            {
                "conversation_id": conversation_id,
                "include_meta": include_meta,
                "max_events": max_events,
            },
        )

    for fn, description in (
        (openhands_list_conversations, None),
        (openhands_get_conversation, None),
        (openhands_get_conversation_status, None),
        (openhands_get_conversation_events, None),
        (openhands_download_trajectory, None),
    ):
        server.add_tool(fn, description=description)

    # --- health / readiness / discovery ---------------------------------
    @server.custom_route("/healthz", methods=["GET"])
    async def healthz(_: Request) -> Response:
        return JSONResponse({"status": "ok", "server": SERVER_NAME})

    @server.custom_route("/readyz", methods=["GET"])
    async def readyz(_: Request) -> Response:
        return JSONResponse({"status": "ready", "upstream": config.openhands_base_url})

    @server.custom_route("/.well-known/oauth-protected-resource", methods=["GET"])
    async def protected_resource(_: Request) -> Response:
        return JSONResponse(_protected_resource_metadata(config))

    @server.custom_route("/.well-known/oauth-authorization-server", methods=["GET"])
    async def authorization_server(_: Request) -> Response:
        return JSONResponse(_authorization_server_metadata(config))

    return server


def _protected_resource_metadata(config: Config) -> dict[str, Any]:
    document: dict[str, Any] = {
        "resource": config.public_mcp_url,
        "bearer_methods_supported": ["header"],
        "scopes_supported": ["openhands.read"],
    }
    if config.oauth_issuer:
        document["authorization_servers"] = [config.oauth_issuer.rstrip("/")]
    return document


def _authorization_server_metadata(config: Config) -> dict[str, Any]:
    if not config.oauth_issuer:
        return {"error": "oauth_not_configured"}
    issuer = config.oauth_issuer.rstrip("/")
    return {
        "issuer": issuer,
        "authorization_endpoint": f"{issuer}/authorize",
        "token_endpoint": f"{issuer}/oauth/token",
        "jwks_uri": f"{issuer}/.well-known/jwks.json",
        "registration_endpoint": f"{issuer}/oauth/register",
        "response_types_supported": ["code"],
        "grant_types_supported": ["authorization_code", "refresh_token"],
        "code_challenge_methods_supported": ["S256"],
        "token_endpoint_auth_methods_supported": ["none", "client_secret_post"],
        "scopes_supported": ["openhands.read"],
    }


class EndpointSecurityMiddleware:
    """Raw ASGI middleware enforcing authentication and rate limiting.

    Written as a raw ASGI middleware (not ``BaseHTTPMiddleware``) so that
    streaming MCP responses and lifespan events pass through untouched.
    """

    def __init__(
        self,
        app: Any,
        *,
        authenticator: Authenticator,
        limiter: RateLimiter,
        protected_paths: list[str],
        public_paths: frozenset[str] | None = None,
        resource_metadata_url: str | None = None,
    ) -> None:
        self.app = app
        self.authenticator = authenticator
        self.limiter = limiter
        self.protected_paths = {p.rstrip("/") or "/" for p in protected_paths}
        self.public_paths = public_paths or _PUBLIC_PATHS
        self.resource_metadata_url = resource_metadata_url

    def _is_protected(self, path: str) -> bool:
        normalized = path.rstrip("/") or "/"
        if normalized in self.public_paths or path in self.public_paths:
            return False
        if normalized.startswith("/.well-known/"):
            return False
        return normalized in self.protected_paths

    async def __call__(self, scope, receive, send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        path = scope.get("path", "/")
        if not self._is_protected(path):
            await self.app(scope, receive, send)
            return

        headers = {
            k.decode("latin-1").lower(): v.decode("latin-1") for k, v in scope.get("headers", [])
        }
        result = self.authenticator.authenticate(headers.get("authorization"))

        if not result.authenticated:
            await self._send_json(
                send,
                401,
                {
                    "error": "invalid_token",
                    "error_description": "A valid bearer token is required.",
                },
                extra_headers=self._www_authenticate_headers(),
            )
            return

        client_host = (scope.get("client") or ("unknown", 0))[0]
        principal = f"{result.subject or 'anon'}:{client_host}"
        allowed, retry_after = await self.limiter.check(principal)
        if not allowed:
            await self._send_json(
                send,
                429,
                {"error": "rate_limited", "error_description": "Too many requests."},
                extra_headers=[(b"retry-after", str(int(retry_after) + 1).encode())],
            )
            return

        scope.setdefault("state", {})
        if isinstance(scope.get("state"), dict):
            scope["state"]["auth_subject"] = result.subject
            scope["state"]["auth_scheme"] = result.scheme

        await self.app(scope, receive, send)

    def _www_authenticate_headers(self) -> list[tuple[bytes, bytes]]:
        value = 'Bearer error="invalid_token"'
        if self.resource_metadata_url:
            value += f', resource_metadata="{self.resource_metadata_url}"'
        return [(b"www-authenticate", value.encode())]

    async def _send_json(
        self,
        send,
        status: int,
        payload: dict[str, Any],
        *,
        extra_headers: list[tuple[bytes, bytes]] | None = None,
    ) -> None:
        body = json.dumps(payload).encode()
        headers = [
            (b"content-type", b"application/json"),
            (b"content-length", str(len(body)).encode()),
            (b"cache-control", b"no-store"),
        ]
        if extra_headers:
            headers.extend(extra_headers)
        await send({"type": "http.response.start", "status": status, "headers": headers})
        await send({"type": "http.response.body", "body": body})


def _build_authenticator(config: Config) -> Authenticator:
    oauth = None
    if config.oauth_issuer:
        oauth = OAuthJWTAuthenticator(config.oauth_issuer, config.oauth_audience)
    return Authenticator(
        static_tokens=config.mcp_auth_tokens,
        oauth=oauth,
        required=config.require_auth,
    )


def _public_base(config: Config) -> str:
    if config.resource_url:
        base = config.resource_url
        for suffix in (config.mount_path, "/"):
            base = base.removesuffix(suffix)
        return base.rstrip("/")
    return f"http://{config.host}:{config.port}"


def create_app(
    config: Config | None = None,
    *,
    client: OpenHandsClient | None = None,
) -> Starlette:
    """Build the full ASGI application (MCP transport + security middleware)."""

    config = config or load_config()
    logging.basicConfig(
        level=getattr(logging, config.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    owns_client = client is None
    api_client = client or OpenHandsClient(config)
    service = ToolService(client=api_client)
    server = build_server(config, service)

    from contextlib import asynccontextmanager

    base_app = server.streamable_http_app()
    # ``streamable_http_app`` owns a lifespan that starts/stops the Streamable
    # HTTP session manager; we must keep it running around our own lifespan.
    inner_lifespan = base_app.router.lifespan_context

    @asynccontextmanager
    async def lifespan(app: Starlette):
        async with inner_lifespan(app):
            try:
                yield
            finally:
                if owns_client:
                    await api_client.aclose()

    app = Starlette(
        routes=list(base_app.routes),
        middleware=[],
        lifespan=lifespan,
        debug=False,
    )

    authenticator = _build_authenticator(config)
    limiter = RateLimiter(
        capacity=config.rate_limit_requests,
        window_seconds=config.rate_limit_window_seconds,
    )
    protected = [config.mount_path, "/sse", "/messages"]

    app.add_middleware(
        EndpointSecurityMiddleware,
        authenticator=authenticator,
        limiter=limiter,
        protected_paths=protected,
        resource_metadata_url=f"{_public_base(config)}/.well-known/oauth-protected-resource",
    )

    if config.trusted_hosts:
        from starlette.middleware.trustedhost import TrustedHostMiddleware

        app.add_middleware(TrustedHostMiddleware, allowed_hosts=config.trusted_hosts)

    app.state.config = config
    app.state.authenticator = authenticator
    app.state.tool_service = service
    app.state.openhands_client = api_client
    return app


def build_asgi_app() -> Starlette:
    """Entry point used by ``uvicorn openhands_mcp_bridge.server:build_asgi_app --factory``."""

    return create_app()
