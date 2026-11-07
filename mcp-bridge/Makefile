# Developer shortcuts. `make help` lists the targets.
SHELL := /bin/bash
PY := .venv/bin/python
PYTHONPATH := src

.DEFAULT_GOAL := help
.PHONY: help venv install test lint format check run stdio docker archive clean

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

venv: ## Create the virtualenv
	uv venv .venv

install: venv ## Install the package with dev extras
	uv pip install --python $(PY) -e ".[dev]"

test: ## Run the test suite
	PYTHONPATH=$(PYTHONPATH) $(PY) -m pytest

lint: ## Lint (ruff)
	uvx ruff check src tests

format: ## Auto-fix lint findings
	uvx ruff check src tests --fix

check: ## Validate configuration and upstream connectivity
	PYTHONPATH=$(PYTHONPATH) $(PY) -m openhands_mcp_bridge check

run: ## Serve MCP over Streamable HTTP
	PYTHONPATH=$(PYTHONPATH) $(PY) -m openhands_mcp_bridge http

stdio: ## Serve MCP over stdio (local clients)
	PYTHONPATH=$(PYTHONPATH) $(PY) -m openhands_mcp_bridge stdio

docker: ## Build the container image
	docker build -t openhands-mcp-bridge:1.0.0 .

archive: ## Build the distributable source ZIP
	python3 scripts/build_archive.py

clean: ## Remove caches and build artefacts
	rm -rf .pytest_cache .ruff_cache dist build src/*.egg-info
	find . -name __pycache__ -type d -prune -exec rm -rf {} +
