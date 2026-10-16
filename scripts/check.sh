#!/usr/bin/env bash
# All project checks. The single source of truth for the Claude Code Stop hook
# (.claude/settings.json, with --hook) and CI (.github/workflows/ci.yml).
#
#   scripts/check.sh               run every check
#   scripts/check.sh NAME...       run the named checks (CI runs one per step)
#   scripts/check.sh --hook        Stop hook mode, see below
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

android_checks=(build unit-tests lint format)
pipeline_checks=(py-format py-lint py-types py-tests fixtures)

run_check() {
  case $1 in
    forbidden-files) bash scripts/check-forbidden-files.sh ;;
    build) ./gradlew --console=plain assembleDebug ;;
    unit-tests) ./gradlew --console=plain testDebugUnitTest ;;
    lint) ./gradlew --console=plain lint ;;
    format) ./gradlew --console=plain spotlessCheck ;;
    py-format) (cd pipeline && uv run ruff format --check .) ;;
    py-lint) (cd pipeline && uv run ruff check .) ;;
    py-types) (cd pipeline && uv run mypy .) ;;
    py-tests) (cd pipeline && uv run pytest) ;;
    # Fails if docs/fixtures/ differs from what the fixture spec generates.
    fixtures) (cd pipeline && uv run python -m vgmixr_pipeline.fixtures.generate --check) ;;
    *)
      echo "Unknown check: $1" >&2
      return 1
      ;;
  esac
}

# Explicit `|| return`: set -e is ignored when the caller is an `if`.
run_checks() {
  local names=(forbidden-files "${android_checks[@]}")
  if [[ -d pipeline ]]; then
    names+=("${pipeline_checks[@]}")
  fi
  for name in "${names[@]}"; do
    run_check "$name" || return
  done
}

if [[ $# -gt 0 && $1 != --hook ]]; then
  for name in "$@"; do
    run_check "$name"
  done
  exit
fi

if [[ $# -eq 0 ]]; then
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
