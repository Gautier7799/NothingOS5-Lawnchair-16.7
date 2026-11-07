"""Tests for the OAuth/JWT path of the authenticator (no network)."""

from __future__ import annotations

import pytest

from openhands_mcp_bridge.auth import Authenticator, OAuthJWTAuthenticator

jwt = pytest.importorskip("jwt")


class _StubOAuth:
    """Deterministic stand-in for the JWKS-backed verifier."""

    def __init__(self, valid_tokens):
        self.valid = set(valid_tokens)
        self.calls = []

    def verify(self, token):
        self.calls.append(token)
        from openhands_mcp_bridge.auth import AuthResult

        if token in self.valid:
            return AuthResult(True, subject="user-1", scheme="oauth")
        return AuthResult(False, reason="invalid_token")


def test_oauth_token_is_accepted():
    oauth = _StubOAuth(["jwt-token-value"])
    auth = Authenticator(static_tokens=["static"], oauth=oauth)
    result = auth.authenticate("Bearer jwt-token-value")
    assert result.authenticated is True
    assert result.scheme == "oauth"
    assert result.subject == "user-1"


def test_static_token_short_circuits_oauth():
    oauth = _StubOAuth(["jwt-token-value"])
    auth = Authenticator(static_tokens=["static"], oauth=oauth)
    assert auth.authenticate("Bearer static").scheme == "bearer"
    assert oauth.calls == []


def test_oauth_rejects_unknown_token():
    oauth = _StubOAuth(["known"])
    auth = Authenticator(static_tokens=["static"], oauth=oauth)
    result = auth.authenticate("Bearer unknown")
    assert result.authenticated is False
    assert result.reason == "invalid_token"


def test_oauth_only_mode_requires_a_token():
    auth = Authenticator(static_tokens=[], oauth=_StubOAuth(["known"]))
    assert auth.enabled is True
    assert auth.authenticate(None).authenticated is False
    assert auth.authenticate("Bearer known").authenticated is True


def test_oauth_verifier_rejects_malformed_token_without_network():
    # jwks_url is supplied so construction never performs discovery.
    verifier = OAuthJWTAuthenticator(
        "https://issuer.example.com",
        "https://bridge.example.com/mcp",
        jwks_url="https://issuer.example.com/.well-known/jwks.json",
    )
    result = verifier.verify("not-a-jwt")
    assert result.authenticated is False
    assert result.reason == "malformed_token"


def test_oauth_verifier_rejects_token_signed_by_unknown_key(monkeypatch):
    verifier = OAuthJWTAuthenticator(
        "https://issuer.example.com",
        None,
        jwks_url="https://issuer.example.com/.well-known/jwks.json",
    )

    token = jwt.encode(
        {"sub": "x", "iss": "https://issuer.example.com"},
        "unit-test-signing-key-of-sufficient-length",
        algorithm="HS256",
    )

    class _FailingClient:
        def __init__(self, *args, **kwargs):
            pass

        def get_signing_key_from_jwt(self, _token):
            raise RuntimeError("no matching key")

    monkeypatch.setattr(jwt, "PyJWKClient", _FailingClient)
    result = verifier.verify(token)
    assert result.authenticated is False
    assert result.reason == "invalid_token"
