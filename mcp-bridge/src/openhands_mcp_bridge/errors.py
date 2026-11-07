"""Structured, secret-safe error types shared across the bridge."""

from __future__ import annotations

from typing import Any


class BridgeError(Exception):
    """Base class for every error surfaced by the bridge.

    ``to_dict`` is what the MCP tools return to the model, so it must never
    contain credentials. Upstream message bodies are truncated and passed
    through :func:`openhands_mcp_bridge.redaction.redact_text` before use.
    """

    code = "bridge_error"
    http_status = 500

    def __init__(self, message: str, *, details: dict | None = None):
        super().__init__(message)
        self.message = message
        self.details: dict = details or {}

    def to_dict(self) -> dict:
        payload: dict[str, Any] = {"error": True, "code": self.code, "message": self.message}
        if self.details:
            payload["details"] = self.details
        return payload


class ConfigurationError(BridgeError):
    code = "configuration_error"
    http_status = 500


class AuthenticationError(BridgeError):
    """Raised when the *MCP client* fails endpoint authentication."""

    code = "unauthorized"
    http_status = 401


class UpstreamAuthError(BridgeError):
    """Raised when OpenHands Cloud rejects our API key."""

    code = "upstream_unauthorized"
    http_status = 502


class UpstreamError(BridgeError):
    code = "upstream_error"
    http_status = 502


class UpstreamTimeoutError(BridgeError):
    code = "upstream_timeout"
    http_status = 504


class NotFoundError(BridgeError):
    code = "not_found"
    http_status = 404


class InvalidInputError(BridgeError):
    code = "invalid_input"
    http_status = 400


class RateLimitedError(BridgeError):
    code = "rate_limited"
    http_status = 429


class ResponseTooLargeError(BridgeError):
    code = "response_too_large"
    http_status = 413
