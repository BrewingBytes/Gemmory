#!/usr/bin/env bash
# Tests ios-gate.sh (T60) with a stub `gh` on PATH: canned labels and `ios` check runs in, the posted status (or none)
# out. Run by ci.yml; locally: `.github/scripts/ios-gate-test.sh`.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"

# The stub answers `gh pr view` with $STUB_LABELS (one per line), `gh api …/check-runs?check_name=ios…` by running the
# script's --jq filter over $STUB_RUNS (and `ios-kotlin` over $STUB_KOTLIN), and records `gh api --method POST
# …/statuses` in $work/posted.
cat > "$work/bin/gh" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
if [ "$1" = pr ]; then printf '%s' "${STUB_LABELS:-}"; exit 0; fi
if [ "$1" = api ] && [ "$2" = --method ]; then
  for arg in "$@"; do case "$arg" in state=*|description=*) echo "$arg" >> "$STUB_POSTED" ;; esac; done
  exit 0
fi
if [ "$1" = api ]; then
  case "$2" in *check_name=ios-kotlin*) runs=$STUB_KOTLIN ;; *) runs=$STUB_RUNS ;; esac
  shift 2
  [ "$1" = --jq ] && jq -r "$2" <<<"$runs"
  exit 0
fi
echo "unexpected gh call: $*" >&2; exit 1
STUB
chmod +x "$work/bin/gh"

export PATH="$work/bin:$PATH" GITHUB_REPOSITORY=o/r GITHUB_SERVER_URL=https://github.com GITHUB_RUN_ID=1
export STUB_POSTED="$work/posted"
failures=0

# check <name> <expected state or "none"> <labels> <ios runs json> [ios result] [ios-kotlin result]
# `ios-kotlin` is green unless $KOTLIN (its runs json) says otherwise.
check() {
  local name=$1 want=$2
  export STUB_LABELS=$3 STUB_RUNS=$4 STUB_KOTLIN=${KOTLIN:-$(runs "$green")}
  : > "$STUB_POSTED"
  "$here/ios-gate.sh" 7 0123456789abcdef "${5:-}" "${6:-}" > /dev/null
  local got
  got=$(sed -n 's/^state=//p' "$STUB_POSTED")
  got=${got:-none}
  if [ "$got" = "$want" ]; then echo "ok   $name"; else echo "FAIL $name: want $want, got $got"; failures=$((failures + 1)); fi
}

run() { printf '{"status":"%s","conclusion":%s,"started_at":"%s"}' "$1" "$2" "$3"; }
runs() { local IFS=,; echo "{\"check_runs\":[$*]}"; }
none='{"check_runs":[]}'
green=$(run completed '"success"' 2026-11-11T10:00:00Z)
red=$(run completed '"failure"' 2026-11-11T10:00:00Z)
building=$(run in_progress null 2026-11-11T10:05:00Z)
label_skip=$(run completed '"skipped"' 2026-11-11T10:30:00Z)
cancelled=$(run completed '"cancelled"' 2026-11-11T10:00:00Z)

check 'hotfix label passes at once'              success 'android-hotfix' "$none"
check 'hotfix label beats a red build'           success $'ui\nandroid-hotfix' "$(runs "$red")"
check 'no run yet is pending'                    pending ''  "$none"
check 'a running build is pending'               pending ''  "$(runs "$building")"
check 'a green build passes'                     success ''  "$(runs "$green")"
check 'a red build fails'                        failure ''  "$(runs "$red")"
check 'a label-change skip is not a verdict'     success ''  "$(runs "$green" "$label_skip")"
check 'a label-change skip over a red build'     failure ''  "$(runs "$red" "$label_skip")"
check 'only skips is pending'                    pending ''  "$(runs "$label_skip")"
check 'the newest real run wins'                 pending ''  "$(runs "$green" "$building")"
check 'a timed-out run (newest, cancelled) fails' failure ''  "$(runs "$cancelled")"
check 'a run replaced by a newer one waits'      pending ''  "$(runs "$cancelled" "$building")"
check 'job results both success'                success ''  "$none" success success
check 'job result failure'                       failure ''  "$none" failure success
check 'job cancelled by its time limit fails'    failure ''  "$(runs "$cancelled")" cancelled success
check 'job cancelled by a newer run waits'       pending ''  "$(runs "$cancelled" "$building")" cancelled success
check 'job cancelled, newer run already green'   success ''  "$(runs "$cancelled" "$(run completed '"success"' 2026-11-11T10:20:00Z)")" cancelled success
check 'hotfix label over a cancelled job result' success 'android-hotfix' "$none" cancelled cancelled

# The second job, ios-kotlin.
check 'kotlin job failure fails a green app'     failure ''  "$none" success failure
KOTLIN=$(runs "$cancelled") check 'kotlin timed out fails'       failure ''  "$none" success cancelled
KOTLIN=$(runs "$building") check 'kotlin still running waits'     pending ''  "$(runs "$green")"
KOTLIN=$(runs "$red")      check 'kotlin red fails, app green'    failure ''  "$(runs "$green")"
KOTLIN=$(runs "$red")      check 'hotfix label beats a red kotlin' success 'android-hotfix' "$(runs "$green")"
KOTLIN=$none               check 'no kotlin run yet waits'        pending ''  "$(runs "$green")"

[ "$failures" -eq 0 ] || { echo "$failures failed"; exit 1; }
echo 'all passed'
