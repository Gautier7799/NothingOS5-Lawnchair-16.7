"""Tests for the MCP endpoint authenticator."""

from __future__ import annotations

from openhands_mcp_bridge.auth import Authenticator, StaticTokenAuthenticator


def test_static_token_accepts_correct_token():
    auth = Authenticator(static_tokens=["token-a", "token-b"])
    assert auth.authenticate("Bearer token-a").authenticated
    assert auth.authenticate("bearer token-b").authenticated


def test_static_token_rejects_wrong_token():
    auth = Authenticator(static_tokens=["token-a"])
    result = auth.authenticate("Bearer nope")
    assert result.authenticated is False
    assert result.reason == "invalid_token"


def test_static_token_rejects_missing_header():
    auth = Authenticator(static_tokens=["token-a"])
    assert auth.authenticate(None).authenticated is False
    assert auth.authenticate("Basic abc").authenticated is False
    assert auth.authenticate("Bearer").authenticated is False


def test_empty_token_is_rejected():
    auth = Authenticator(static_tokens=["token-a"])
    assert auth.authenticate("Bearer ").authenticated is False


def test_static_authenticator_disabled_without_tokens():
    assert StaticTokenAuthenticator([]).enabled is False


def test_authenticator_with_no_scheme_allows_when_not_required():
    auth = Authenticator(static_tokens=[], required=False)
    result = auth.authenticate(None)
    assert result.authenticated is True
    assert result.subject == "anonymous"


def test_authenticator_with_no_scheme_rejects_when_required():
    auth = Authenticator(static_tokens=[], required=True)
    assert auth.authenticate(None).authenticated is False


def test_oauth_present_but_token_is_static_fallback():
    class FakeOAuth:
        def verify(self, token):  # pragma: no cover - not reached
            raise AssertionError("oauth should not be consulted for static tokens")

    auth = Authenticator(static_tokens=["static-token"], oauth=FakeOAuth())
    assert auth.authenticate("Bearer static-token").scheme == "bearer"
