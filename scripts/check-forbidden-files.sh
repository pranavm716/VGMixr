#!/usr/bin/env bash
# Fails if ROMs, saves, extracted audio or pipeline output are tracked in git.
# Checks the index, so staged-but-uncommitted files are caught too.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

rom_re='\.(gb|gbc|gba|nds|3ds|cia|cci|cxi|nsp|xci|sav)$'
audio_re='\.(flac|mp3|ogg|opus|wav|m4a)$'
# Tiny synthetic test audio is allowed here (mirrors .gitignore). ROMs never are.
audio_exempt_re='^(app/src/test/resources|pipeline/tests/data)/'

shopt -s nocasematch
bad=()
while IFS= read -r -d '' path; do
  if [[ $path =~ ^pipeline/out/ || $path =~ $rom_re ]]; then
    bad+=("$path")
  elif [[ $path =~ $audio_re && ! $path =~ $audio_exempt_re ]]; then
    bad+=("$path")
  fi
done < <(git ls-files -z)

if ((${#bad[@]})); then
  echo "Forbidden files are tracked (ROMs, saves, audio or pipeline/out/):" >&2
  printf '  %s\n' "${bad[@]}" >&2
  echo "Remove them with: git rm --cached <path>" >&2
  exit 1
fi
