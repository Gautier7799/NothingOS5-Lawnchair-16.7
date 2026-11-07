"""Application configuration.

Every value is read from the environment. Secrets are never written to disk by
this module and are never included in ``repr`` output (see ``Config.__repr__``).
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, field

DEFAULT_BASE_URL = "https://app.all-hands.dev"
DEFAULT_API_PREFIX = "/api/v1"

#: The conversation that must always be retrievable (acceptance requirement).
DEFAULT_CONVERSATION_ID = "7177c0cd42934b7db0ec620706f3f547"


def _env(name: str, default: str | None = None, env: Mapping[str, str] | None = None) -> str | None:
    source = os.environ if env is None else env
    value = source.get(name)
    if value is None:
        return default
    value = value.strip()
    return value or default


def _env_int(name: str, default: int, env: Mapping[str, str] | None = None) -> int:
    raw = _env(name, env=env)
    if raw is None:
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _env_float(name: str, default: float, env: Mapping[str, str] | None = None) -> float:
    raw = _env(name, env=env)
    if raw is None:
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def _env_bool(name: str, default: bool, env: Mapping[str, str] | None = None) -> bool:
    raw = _env(name, env=env)
    if raw is None:
        return default
    return raw.lower() in {"1", "true", "yes", "on"}


def _env_list(name: str, env: Mapping[str, str] | None = None) -> list[str]:
    raw = _env(name, env=env)
    if not raw:
        return []
    return [item.strip() for item in raw.split(",") if item.strip()]


def _read_secret_file(path: str) -> str | None:
    try:
        with open(path, encoding="utf-8") as handle:
            return handle.read().strip() or None
    except OSError:
        return None


def _secret(name: str, env: Mapping[str, str] | None = None) -> str | None:
    """Read a secret from ``<NAME>`` or from the file named by ``<NAME>_FILE``.

    The file indirection lets Docker/Kubernetes secret mounts be used without
    ever putting the value in the process environment or image layers.
    """

    direct = _env(name, env=env)
    if direct:
        return direct
    path = _env(f"{name}_FILE", env=env)
    if path:
        return _read_secret_file(path)
    return None


@dataclass(frozen=True)
class Config:
    """Immutable runtime configuration."""

    # --- OpenHands Cloud -------------------------------------------------
    openhands_api_key: str
    openhands_base_url: str = DEFAULT_BASE_URL
    openhands_api_prefix: str = DEFAULT_API_PREFIX
    http_timeout: float = 30.0
    http_connect_timeout: float = 10.0
    http_max_retries: int = 3

    # --- MCP transport ---------------------------------------------------
    # Binding to all interfaces is the intended default inside a container;
    # TLS and ingress are expected to sit in front of the process.
    host: str = "0.0.0.0"  # noqa: S104
    port: int = 12000
    mount_path: str = "/mcp"
    resource_url: str | None = None

    # --- MCP endpoint authentication ------------------------------------
    #: Static bearer tokens accepted on the MCP endpoint (the ChatGPT
    #: "API key" mode). Empty list disables static-token auth.
    mcp_auth_tokens: list[str] = field(default_factory=list)
    #: Public OAuth 2.1 metadata advertised to clients. When set, ChatGPT can
    #: also authenticate through the authorization-code + PKCE flow.
    oauth_issuer: str | None = None
    oauth_audience: str | None = None
    #: If true, the ASGI app refuses to start without a usable auth mode.
    require_auth: bool = True

    # --- Limits / hardening ---------------------------------------------
    rate_limit_requests: int = 60
    rate_limit_window_seconds: float = 60.0
    max_event_page_limit: int = 100
    max_list_page_limit: int = 100
    max_events_returned: int = 1000
    max_event_bytes: int = 200_000
    max_response_bytes: int = 4_000_000
    max_trajectory_download_bytes: int = 128 * 1024 * 1024
    max_trajectory_events_returned: int = 500
    max_trajectory_event_bytes: int = 200_000
    trusted_hosts: list[str] = field(default_factory=list)
    log_level: str = "INFO"

    @property
    def api_base(self) -> str:
        return f"{self.openhands_base_url.rstrip('/')}{self.openhands_api_prefix}"

    @property
    def auth_enabled(self) -> bool:
        return bool(self.mcp_auth_tokens) or bool(self.oauth_issuer)

    @property
    def public_mcp_url(self) -> str:
        if self.resource_url:
            return self.resource_url
        return f"http://{self.host}:{self.port}{self.mount_path}"

    def __repr__(self) -> str:  # pragma: no cover - trivial, but security relevant
        # Never leak secrets through logging / tracebacks.
        return (
            "Config("
            f"openhands_api_key=<redacted:{len(self.openhands_api_key)} chars>, "
            f"openhands_base_url={self.openhands_base_url!r}, "
            f"mcp_auth_tokens=<redacted:{len(self.mcp_auth_tokens)} tokens>, "
            f"oauth_issuer={self.oauth_issuer!r}, "
            f"host={self.host!r}, port={self.port!r}, mount_path={self.mount_path!r}, "
            f"require_auth={self.require_auth!r})"
        )


class ConfigError(RuntimeError):
    """Raised when the environment does not contain a usable configuration."""


def load_config(env: Mapping[str, str] | None = None) -> Config:
    """Build a :class:`Config` from the environment.

    ``OPENHANDS_CLOUD_API_KEY`` is the canonical variable. For backwards
    compatibility with existing OpenHands tooling ``OPENHANDS_API_KEY`` is
    accepted as a fallback.

    ``env`` overrides process environment values; it is used by tests and never
    mutates ``os.environ``.
    """

    return _load_config(env)


def _load_config(env: Mapping[str, str] | None = None) -> Config:
    api_key = _secret("OPENHANDS_CLOUD_API_KEY", env) or _secret("OPENHANDS_API_KEY", env) or ""
    if not api_key:
        raise ConfigError(
            "OPENHANDS_CLOUD_API_KEY is not set. Export it as a private environment "
            "secret before starting the server (see DEPLOYMENT.md)."
        )

    tokens = _env_list("MCP_AUTH_TOKENS", env)
    single_token = _secret("MCP_AUTH_TOKEN", env)
    if single_token and single_token not in tokens:
        tokens = [single_token, *tokens]

    config = Config(
        openhands_api_key=api_key,
        openhands_base_url=_env("OPENHANDS_BASE_URL", DEFAULT_BASE_URL, env) or DEFAULT_BASE_URL,
        openhands_api_prefix=(
            _env("OPENHANDS_API_PREFIX", DEFAULT_API_PREFIX, env) or DEFAULT_API_PREFIX
        ),
        http_timeout=_env_float("OPENHANDS_HTTP_TIMEOUT", 30.0, env),
        http_connect_timeout=_env_float("OPENHANDS_HTTP_CONNECT_TIMEOUT", 10.0, env),
        http_max_retries=_env_int("OPENHANDS_HTTP_MAX_RETRIES", 3, env),
        host=_env("MCP_HOST", "0.0.0.0", env) or "0.0.0.0",  # noqa: S104
        port=_env_int("MCP_PORT", 12000, env),
        mount_path=_env("MCP_PATH", "/mcp", env) or "/mcp",
        resource_url=_env("MCP_RESOURCE_URL", env=env),
        mcp_auth_tokens=tokens,
        oauth_issuer=_env("MCP_OAUTH_ISSUER", env=env),
        oauth_audience=_env("MCP_OAUTH_AUDIENCE", env=env),
        require_auth=_env_bool("MCP_REQUIRE_AUTH", True, env),
        rate_limit_requests=_env_int("MCP_RATE_LIMIT_REQUESTS", 60, env),
        rate_limit_window_seconds=_env_float("MCP_RATE_LIMIT_WINDOW_SECONDS", 60.0, env),
        max_event_page_limit=_env_int("MCP_MAX_EVENT_PAGE_LIMIT", 100, env),
        max_list_page_limit=_env_int("MCP_MAX_LIST_PAGE_LIMIT", 100, env),
        max_events_returned=_env_int("MCP_MAX_EVENTS_RETURNED", 1000, env),
        max_event_bytes=_env_int("MCP_MAX_EVENT_BYTES", 200_000, env),
        max_response_bytes=_env_int("MCP_MAX_RESPONSE_BYTES", 4_000_000, env),
        max_trajectory_download_bytes=_env_int(
            "MCP_MAX_TRAJECTORY_DOWNLOAD_BYTES", 128 * 1024 * 1024, env
        ),
        max_trajectory_events_returned=_env_int("MCP_MAX_TRAJECTORY_EVENTS_RETURNED", 500, env),
        max_trajectory_event_bytes=_env_int("MCP_MAX_TRAJECTORY_EVENT_BYTES", 200_000, env),
        trusted_hosts=_env_list("MCP_TRUSTED_HOSTS", env),
        log_level=(_env("MCP_LOG_LEVEL", "INFO", env) or "INFO").upper(),
    )

    if config.require_auth and not config.auth_enabled:
        raise ConfigError(
            "No MCP endpoint authentication configured. Set MCP_AUTH_TOKEN "
            "(and/or MCP_AUTH_TOKENS / MCP_OAUTH_ISSUER). Refusing to start an "
            "unauthenticated remote MCP endpoint."
        )
    return config
