"""In-process token-bucket rate limiting.

The bridge is a single-process ASGI app; a process-local limiter is enough to
protect both the MCP endpoint and the upstream OpenHands Cloud API from
runaway loops. For multi-replica deployments put a limiter at the ingress too.
"""

from __future__ import annotations

import asyncio
import time
from dataclasses import dataclass


@dataclass
class _Bucket:
    tokens: float
    updated_at: float


class RateLimiter:
    """Sliding-window (token bucket) limiter keyed by an arbitrary string."""

    def __init__(self, capacity: int, window_seconds: float) -> None:
        if capacity <= 0:
            raise ValueError("capacity must be positive")
        if window_seconds <= 0:
            raise ValueError("window_seconds must be positive")
        self.capacity = float(capacity)
        self.window_seconds = float(window_seconds)
        self._refill_rate = self.capacity / self.window_seconds
        self._buckets: dict[str, _Bucket] = {}
        self._lock = asyncio.Lock()

    async def check(self, key: str, cost: float = 1.0) -> tuple[bool, float]:
        """Consume ``cost`` tokens for ``key``.

        Returns ``(allowed, retry_after_seconds)``. ``retry_after_seconds`` is 0
        when the request is allowed.
        """

        now = time.monotonic()
        async with self._lock:
            bucket = self._buckets.get(key)
            if bucket is None:
                bucket = _Bucket(tokens=self.capacity, updated_at=now)
                self._buckets[key] = bucket

            elapsed = now - bucket.updated_at
            bucket.tokens = min(self.capacity, bucket.tokens + elapsed * self._refill_rate)
            bucket.updated_at = now

            if bucket.tokens >= cost:
                bucket.tokens -= cost
                self._maybe_sweep(now)
                return True, 0.0

            deficit = cost - bucket.tokens
            return False, deficit / self._refill_rate

    def _maybe_sweep(self, now: float) -> None:
        # Bounded memory: drop buckets that have been idle for a full window.
        if len(self._buckets) < 10_000:
            return
        cutoff = now - self.window_seconds
        for key in [k for k, b in self._buckets.items() if b.updated_at < cutoff]:
            self._buckets.pop(key, None)
