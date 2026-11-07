"""Secret redaction helpers.

The OpenHands event stream can echo conversation settings which contain
``api_key`` fields. Everything that leaves the bridge towards ChatGPT is passed
through :func:`redact` so those values become ``**********``.
"""

from __future__ import annotations

import re
from collections.abc import Iterable
from typing import Any

REDACTED = "**********"

#: Keys whose values are always replaced, wherever they appear in a payload.
SENSITIVE_KEYS = frozenset(
    {
        "api_key",
        "apikey",
        "api_key_encrypted",
        "authorization",
        "access_token",
        "refresh_token",
        "id_token",
        "client_secret",
        "password",
        "secret",
        "session_api_key",
        "x-session-api-key",
        "x-access-token",
        "provider_tokens",
        "search_api_key",
        "sandbox_api_key",
        "litellm_api_key",
        "openai_api_key",
        "anthropic_api_key",
    }
)

#: Value patterns that look like credentials even when the key is innocuous.
_VALUE_PATTERNS: tuple[re.Pattern[str], ...] = (
    re.compile(r"\bsk-[A-Za-z0-9_\-]{12,}\b"),
    re.compile(r"\bsess-[A-Za-z0-9]{16,}\b"),
    re.compile(r"\bBearer\s+[A-Za-z0-9._\-]{12,}\b", re.IGNORECASE),
    re.compile(r"\beyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\b"),
)


def _is_sensitive_key(key: Any) -> bool:
    if not isinstance(key, str):
        return False
    lowered = key.lower()
    if lowered in SENSITIVE_KEYS:
        return True
    return lowered.endswith(("_api_key", "_secret", "_token"))


def redact(value: Any, *, extra_secrets: Iterable[str] = ()) -> Any:
    """Recursively redact sensitive keys and credential-looking values."""

    extra = tuple(secret for secret in extra_secrets if secret and len(secret) >= 8)
    return _redact(value, extra)


def _redact(value: Any, extra: tuple[str, ...]) -> Any:
    if isinstance(value, dict):
        out: dict[Any, Any] = {}
        for key, item in value.items():
            if _is_sensitive_key(key):
                out[key] = REDACTED
            else:
                out[key] = _redact(item, extra)
        return out
    if isinstance(value, (list, tuple)):
        return [_redact(item, extra) for item in value]
    if isinstance(value, str):
        return redact_text(value, extra_secrets=extra)
    return value


def redact_text(text: str, *, extra_secrets: Iterable[str] = ()) -> str:
    """Redact credential-looking substrings inside a free-form string."""

    if not isinstance(text, str):
        return text
    result = text
    for secret in extra_secrets:
        if secret and len(secret) >= 8:
            result = result.replace(secret, REDACTED)
    for pattern in _VALUE_PATTERNS:
        result = pattern.sub(REDACTED, result)
    return result
