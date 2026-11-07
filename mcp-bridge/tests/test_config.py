"""Tests for configuration loading and secret handling."""

from __future__ import annotations

import pytest

from openhands_mcp_bridge.config import (
    DEFAULT_CONVERSATION_ID,
    ConfigError,
    load_config,
)

BASE_ENV = {
    "OPENHANDS_CLOUD_API_KEY": "oh-key-123",
    "MCP_AUTH_TOKEN": "mcp-token-abc",
}


def test_missing_api_key_raises(monkeypatch):
    for key in (
        "OPENHANDS_CLOUD_API_KEY",
        "OPENHANDS_API_KEY",
        "MCP_AUTH_TOKEN",
        "MCP_AUTH_TOKENS",
    ):
        monkeypatch.delenv(key, raising=False)
    with pytest.raises(ConfigError):
        load_config()


def test_api_key_fallback_to_legacy_name(monkeypatch):
    monkeypatch.delenv("OPENHANDS_CLOUD_API_KEY", raising=False)
    monkeypatch.setenv("OPENHANDS_API_KEY", "legacy-key")
    monkeypatch.setenv("MCP_AUTH_TOKEN", "t")
    config = load_config()
    assert config.openhands_api_key == "legacy-key"


def test_requires_endpoint_auth(monkeypatch):
    monkeypatch.setenv("OPENHANDS_CLOUD_API_KEY", "oh-key")
    monkeypatch.delenv("MCP_AUTH_TOKEN", raising=False)
    monkeypatch.delenv("MCP_AUTH_TOKENS", raising=False)
    monkeypatch.delenv("MCP_OAUTH_ISSUER", raising=False)
    with pytest.raises(ConfigError):
        load_config()


def test_repr_never_contains_secrets():
    config = load_config(BASE_ENV)
    text = repr(config)
    assert "oh-key-123" not in text
    assert "mcp-token-abc" not in text
    assert "<redacted" in text


def test_defaults_and_api_base():
    config = load_config(BASE_ENV)
    assert config.api_base == "https://app.all-hands.dev/api/v1"
    assert config.mount_path == "/mcp"
    assert config.auth_enabled is True


def test_auth_can_be_disabled_explicitly(monkeypatch):
    monkeypatch.setenv("OPENHANDS_CLOUD_API_KEY", "oh-key")
    monkeypatch.setenv("MCP_REQUIRE_AUTH", "false")
    monkeypatch.delenv("MCP_AUTH_TOKEN", raising=False)
    monkeypatch.delenv("MCP_AUTH_TOKENS", raising=False)
    config = load_config()
    assert config.require_auth is False


def test_default_conversation_id_constant():
    assert DEFAULT_CONVERSATION_ID == "7177c0cd42934b7db0ec620706f3f547"


def test_secrets_can_be_read_from_files(tmp_path, monkeypatch):
    key_file = tmp_path / "api_key"
    key_file.write_text("file-based-key\n")
    token_file = tmp_path / "token"
    token_file.write_text("file-based-token\n")

    for name in (
        "OPENHANDS_CLOUD_API_KEY",
        "OPENHANDS_API_KEY",
        "MCP_AUTH_TOKEN",
        "MCP_AUTH_TOKENS",
        "MCP_OAUTH_ISSUER",
    ):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("OPENHANDS_CLOUD_API_KEY_FILE", str(key_file))
    monkeypatch.setenv("MCP_AUTH_TOKEN_FILE", str(token_file))

    config = load_config()
    assert config.openhands_api_key == "file-based-key"
    assert config.mcp_auth_tokens == ["file-based-token"]
    assert "file-based-key" not in repr(config)
    assert "file-based-token" not in repr(config)


def test_missing_secret_file_does_not_crash(monkeypatch):
    monkeypatch.delenv("OPENHANDS_CLOUD_API_KEY", raising=False)
    monkeypatch.delenv("OPENHANDS_API_KEY", raising=False)
    monkeypatch.setenv("OPENHANDS_CLOUD_API_KEY_FILE", "/nonexistent/secret")
    monkeypatch.setenv("MCP_AUTH_TOKEN", "t")
    with pytest.raises(ConfigError):
        load_config()
