"""Authentication for the remote MCP endpoint.

Two modes are supported, and they can be combined:

1. **Static bearer token** (``MCP_AUTH_TOKEN`` / ``MCP_AUTH_TOKENS``).
   ChatGPT connector creation accepts an "API key" or "OAuth" credential. With
   static tokens the operator pastes the token into the ChatGPT connector
   dialog; ChatGPT then sends ``Authorization: Bearer <token>``.

2. **OAuth 2.1 / OIDC** (``MCP_OAUTH_ISSUER``, optional ``MCP_OAUTH_AUDIENCE``).
   The server advertises RFC 9728 protected-resource metadata and (when the
   issuer exposes it) RFC 8414 authorization-server metadata so ChatGPT can run
   the authorization-code + PKCE flow. Access tokens are validated by verifying
   the JWT signature against the issuer's JWKS.

Only the SHA-256 hash of a static token is kept in memory after startup and all
comparisons are constant time. Tokens are never logged.
"""

from __future__ import annotations

import hashlib
import hmac
import logging
import time
from collections.abc import Iterable
from dataclasses import dataclass

import httpx

logger = logging.getLogger(__name__)

_JWKS_CACHE_TTL = 600.0


@dataclass
class AuthResult:
    authenticated: bool
    subject: str | None = None
    scheme: str | None = None
    reason: str | None = None
    retry_after: int | None = None


def _sha256(value: str) -> bytes:
    return hashlib.sha256(value.encode("utf-8")).digest()


class StaticTokenAuthenticator:
    """Constant-time comparison against a set of pre-hashed bearer tokens."""

    def __init__(self, tokens: Iterable[str]) -> None:
        self._hashes = [h for h in (_sha256(t) for t in tokens if t) if h]
        # A throwaway hash keeps the comparison loop's work constant when no
        # tokens are configured (defence in depth against timing leaks).
        self._dummy = _sha256("__unused__")

    @property
    def enabled(self) -> bool:
        return bool(self._hashes)

    def verify(self, token: str) -> bool:
        if not token:
            return False
        candidate = _sha256(token)
        matched = False
        for known in self._hashes or [self._dummy]:
            # ``&`` (not ``and``) keeps the loop constant time.
            matched = hmac.compare_digest(candidate, known) | matched
        return bool(matched) and bool(self._hashes)


class OAuthJWTAuthenticator:
    """Validates RS256/ES256 access tokens against an OIDC issuer's JWKS."""

    def __init__(
        self,
        issuer: str,
        audience: str | None = None,
        *,
        jwks_url: str | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.issuer = issuer.rstrip("/")
        self.audience = audience
        self.jwks_url = jwks_url or self._discover_jwks_url(timeout=timeout)
        self._timeout = timeout
        self._jwks: dict | None = None
        self._jwks_fetched_at = 0.0

    def _discover_jwks_url(self, *, timeout: float) -> str:
        # Try OIDC discovery first, then fall back to the RFC 8414 path.
        candidates = [
            f"{self.issuer}/.well-known/openid-configuration",
            f"{self.issuer}/.well-known/oauth-authorization-server",
        ]
        for url in candidates:
            try:
                response = httpx.get(url, timeout=timeout)
                if response.status_code == 200:
                    data = response.json()
                    if data.get("jwks_uri"):
                        return str(data["jwks_uri"])
            except httpx.HTTPError as exc:
                logger.debug("OIDC discovery failed for %s: %s", url, exc)
                continue
        return f"{self.issuer}/.well-known/jwks.json"

    def _get_jwks(self) -> dict:
        now = time.monotonic()
        if self._jwks is None or (now - self._jwks_fetched_at) > _JWKS_CACHE_TTL:
            response = httpx.get(self.jwks_url, timeout=self._timeout)
            response.raise_for_status()
            self._jwks = response.json()
            self._jwks_fetched_at = now
        return self._jwks or {}

    def verify(self, token: str) -> AuthResult:
        try:
            import jwt  # type: ignore
            from jwt import PyJWKClient  # type: ignore
        except ImportError:  # pragma: no cover - optional dependency
            return AuthResult(False, reason="oauth_not_supported")

        try:
            header = jwt.get_unverified_header(token)
        except Exception:  # noqa: BLE001
            return AuthResult(False, reason="malformed_token")

        try:
            signing_key = PyJWKClient(
                self.jwks_url, timeout=self._timeout
            ).get_signing_key_from_jwt(token)
            options = {"verify_aud": bool(self.audience)}
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=[header.get("alg", "RS256")],
                audience=self.audience,
                issuer=self.issuer,
                options=options,
            )
        except Exception as exc:  # noqa: BLE001 - any failure is a rejection
            logger.info("OAuth token rejected: %s", type(exc).__name__)
            return AuthResult(False, reason="invalid_token")

        return AuthResult(
            True,
            subject=str(claims.get("sub", "unknown")),
            scheme="oauth",
        )


class Authenticator:
    """Facade that applies the configured authentication policy."""

    def __init__(
        self,
        *,
        static_tokens: Iterable[str] = (),
        oauth: OAuthJWTAuthenticator | None = None,
        required: bool = True,
    ) -> None:
        self.static = StaticTokenAuthenticator(static_tokens)
        self.oauth = oauth
        self.required = required

    @property
    def enabled(self) -> bool:
        return self.static.enabled or self.oauth is not None

    @property
    def oauth_enabled(self) -> bool:
        return self.oauth is not None

    def authenticate(self, authorization_header: str | None) -> AuthResult:
        if not self.enabled:
            if self.required:
                # Fail closed: an endpoint that requires authentication but has
                # no usable scheme must never accept an anonymous request.
                return AuthResult(False, reason="no_auth_scheme_configured")
            return AuthResult(True, subject="anonymous", scheme="none")

        token = _extract_bearer(authorization_header)
        if token is None:
            return AuthResult(
                False,
                reason="missing_credentials",
                retry_after=None,
            )

        if self.static.verify(token):
            return AuthResult(True, subject="static-token", scheme="bearer")

        if self.oauth is not None:
            result = self.oauth.verify(token)
            if result.authenticated:
                return result
            return AuthResult(False, reason="invalid_token")

        return AuthResult(False, reason="invalid_token")


def _extract_bearer(authorization_header: str | None) -> str | None:
    if not authorization_header:
        return None
    parts = authorization_header.strip().split(" ", 1)
    if len(parts) != 2:
        return None
    scheme, credentials = parts[0].lower(), parts[1].strip()
    if scheme not in {"bearer", "token"}:
        return None
    return credentials or None


def _is_probably_jwt(token: str) -> bool:
    return token.count(".") == 2 and len(token) > 40
