#!/usr/bin/env bash
# All project checks. The single source of truth for the Claude Code Stop hook
# (.claude/settings.json, with --hook) and CI (.github/workflows/ci.yml).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

# Explicit `|| return`: set -e is ignored when the caller is an `if`.
run_checks() {
  bash scripts/check-forbidden-files.sh || return
  ./gradlew --console=plain assembleDebug testDebugUnitTest lint spotlessCheck || return
  if [[ -d pipeline ]]; then
    # Chained with && for the same reason: set -e is off inside this subshell too.
    # The generator --check fails if docs/fixtures/ differs from what its spec generates.
    (cd pipeline &&
      uv run ruff format --check . &&
      uv run ruff check . &&
      uv run mypy . &&
      uv run pytest &&
      uv run python -m vgmixr_pipeline.fixtures.generate --check) || return
  fi
}

if [[ ${1:-} != --hook ]]; then
  run_checks
  exit
fi

# Hook mode: skip when nothing changed since the last green run; on failure,
# exit 2 so Claude sees the output and keeps working.
cat > /dev/null # drain the hook's JSON input
stamp_file=build/.checks-green-stamp
stamp=$({
  git rev-parse HEAD
  git status --porcelain --untracked-files=all
  git diff HEAD --binary
  git ls-files --others --exclude-standard -z | xargs -0 -r cat 2>/dev/null
} | git hash-object --stdin)

if [[ -f $stamp_file && $(cat "$stamp_file") == "$stamp" ]]; then
  exit 0
fi

log=$(mktemp)
trap 'rm -f "$log"' EXIT
if run_checks > "$log" 2>&1; then
  mkdir -p build
  echo "$stamp" > "$stamp_file"
else
  {
    echo "Checks failed (scripts/check.sh). Last 60 lines:"
    tail -n 60 "$log"
  } >&2
  exit 2
fi
