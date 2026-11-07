FROM python:3.13-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

RUN useradd --create-home --uid 10001 appuser

# Dependencies first for better layer caching.
COPY pyproject.toml README.md ./
COPY src ./src
RUN pip install --no-cache-dir .

# Drop privileges and run as a non-root user.
USER appuser

EXPOSE 12000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:12000/healthz', timeout=3).status==200 else 1)"

# Secrets are injected at runtime (env or *_FILE mounts); nothing is baked in.
CMD ["python", "-m", "openhands_mcp_bridge", "http"]
