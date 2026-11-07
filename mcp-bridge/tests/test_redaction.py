"""Tests for secret redaction."""

from __future__ import annotations

from openhands_mcp_bridge.redaction import REDACTED, redact, redact_text


def test_nested_api_key_is_redacted():
    payload = {"agent": {"llm": {"api_key": "sk-secret-value-123456"}}}
    assert redact(payload)["agent"]["llm"]["api_key"] == REDACTED


def test_lists_are_walked():
    payload = [{"api_key": "a"}, {"name": "ok"}]
    result = redact(payload)
    assert result[0]["api_key"] == REDACTED
    assert result[1]["name"] == "ok"


def test_other_secret_suffixes_are_redacted():
    payload = {
        "session_api_key": "x",
        "client_secret": "y",
        "access_token": "z",
        "password": "p",
        "provider_tokens": {"github": "t"},
    }
    result = redact(payload)
    assert all(value == REDACTED for value in result.values())


def test_credential_looking_values_in_text_are_redacted():
    text = "failed with sk-live-abcdef1234567890 and Bearer abcdefghijklmnopqrst"
    out = redact_text(text)
    assert "sk-live-abcdef1234567890" not in out
    assert "abcdefghijklmnopqrst" not in out


def test_extra_secret_replacement():
    assert redact_text("key is oh-key-123", extra_secrets=["oh-key-123"]) == "key is " + REDACTED


def test_short_extra_secrets_are_ignored():
    # Avoid mangling short, common substrings.
    assert redact_text("abc def", extra_secrets=["abc"]) == "abc def"


def test_non_string_values_pass_through():
    assert redact(5) == 5
    assert redact(None) is None
    assert redact(True) is True
