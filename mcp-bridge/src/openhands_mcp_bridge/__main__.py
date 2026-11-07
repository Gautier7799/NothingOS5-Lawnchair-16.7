"""Command-line entry point.

    python -m openhands_mcp_bridge            # run the HTTP (Streamable HTTP) server
    python -m openhands_mcp_bridge stdio      # run over stdio (for local MCP clients)
    python -m openhands_mcp_bridge check      # verify config + connectivity, then exit
"""

from __future__ import annotations

import argparse
import asyncio
import logging
import sys
from collections.abc import Sequence

from .config import ConfigError, load_config
from .server import SERVER_NAME, build_server, create_app
from .tools import ToolService

logger = logging.getLogger(__name__)


def _run_stdio() -> int:
    from .client import OpenHandsClient

    config = load_config()
    client = OpenHandsClient(config)
    server = build_server(config, ToolService(client=client))
    server.run(transport="stdio")
    return 0


def _run_http() -> int:
    import uvicorn

    config = load_config()
    app = create_app(config)
    logger.info("%s listening on %s:%s%s", SERVER_NAME, config.host, config.port, config.mount_path)
    uvicorn.run(
        app,
        host=config.host,
        port=config.port,
        log_level=config.log_level.lower(),
        proxy_headers=True,
        forwarded_allow_ips="*",
    )
    return 0


async def _check() -> int:
    from .client import OpenHandsClient
    from .config import DEFAULT_CONVERSATION_ID

    config = load_config()
    client = OpenHandsClient(config)
    try:
        status = await client.get_conversation_status(DEFAULT_CONVERSATION_ID)
        print("OpenHands Cloud reachable.")
        print(f"  conversation: {status.get('id')}")
        print(f"  sandbox_status: {status.get('sandbox_status')}")
        print(f"  execution_status: {status.get('execution_status')}")
        print(f"  event_count: {status.get('event_count')}")
    finally:
        await client.aclose()
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="openhands-mcp-bridge", description=__doc__)
    parser.add_argument(
        "command",
        nargs="?",
        default="http",
        choices=["http", "stdio", "check"],
        help="http (default): serve MCP over Streamable HTTP; stdio: local transport; "
        "check: validate configuration and upstream connectivity.",
    )
    args = parser.parse_args(argv)

    try:
        if args.command == "stdio":
            return _run_stdio()
        if args.command == "check":
            return asyncio.run(_check())
        return _run_http()
    except ConfigError as exc:
        print(f"configuration error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
