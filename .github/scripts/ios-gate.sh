#!/usr/bin/env bash
# Posts the `ios-gate` commit status on a PR's head commit (T60, eng D25). `ios-gate` is the required check on main,
# not the macOS jobs themselves, so a PR labelled `android-hotfix` can merge on ci.yml alone while the iOS jobs still
# run and report (premise 2: iOS never blocks Android).
#
#   ios-gate.sh <pr number> <head sha> [<ios job result> <ios-kotlin job result>]
#
# With the label: success at once. Without it, each of the two jobs (`ios`, `ios-kotlin`) has a verdict: its result
# when given, or else its latest check run on the commit (pending while it runs). Both green is success, either red is
# failure, otherwise pending. The label is read live, so adding or removing it while the jobs run is honoured by
# whichever caller posts last.
#
# A `skipped` check run is never a verdict: it is a job of a label-change run, which never builds. A `cancelled` job is
# looked up: if a newer run of it exists on the commit (a reopen or "Re-run all jobs" replaced it), that run decides; if
# it is still the newest, it hit the job's time limit or was stopped by hand, and that is a failure.
# ios-gate-test.sh (next to this file, run by ci.yml) covers every branch with a stub `gh`.
set -euo pipefail

pr=$1
sha=$2
repo=$GITHUB_REPOSITORY
run_url="${GITHUB_SERVER_URL}/${repo}/actions/runs/${GITHUB_RUN_ID}"

# verdict <check name> [job result] → success | pending | <failure reason>
verdict() {
  local name=$1 result=${2:-} run
  # A cancelled job's own result can't tell a time-out from being replaced; the check runs on the commit can.
  [ "$result" = cancelled ] && result=
  if [ -n "$result" ]; then
    echo "$result"
    return
  fi
  run=$(gh api "repos/$repo/commits/$sha/check-runs?check_name=$name&filter=all" \
    --jq '.check_runs | map(select(.conclusion != "skipped")) | sort_by(.started_at) | last | "\(.status) \(.conclusion)"')
  case "$run" in
    'completed success') echo success ;;
    'completed cancelled') echo 'cancelled or timed out' ;;
    completed\ *) echo "${run#completed }" ;;
    *) echo pending ;;
  esac
}

labels=$(gh pr view "$pr" --repo "$repo" --json labels --jq '.labels[].name')
if grep -qx 'android-hotfix' <<<"$labels"; then
  state=success
  description='android-hotfix: merges on ci.yml alone; the iOS jobs still run and report'
else
  app=$(verdict ios "${3:-}")
  kotlin=$(verdict ios-kotlin "${4:-}")
  if [ "$app" = success ] && [ "$kotlin" = success ]; then
    state=success description='iOS build, XCTests and Kotlin simulator tests passed'
  elif [ "$app" != success ] && [ "$app" != pending ]; then
    state=failure description="iOS build or XCTests (ios): $app"
  elif [ "$kotlin" != success ] && [ "$kotlin" != pending ]; then
    state=failure description="Kotlin simulator tests (ios-kotlin): $kotlin"
  else
    state=pending description='Waiting for the iOS jobs (ios, ios-kotlin)'
  fi
fi

echo "ios-gate on ${sha::7}: $state ($description)"
gh api --method POST "repos/$repo/statuses/$sha" \
  -f state="$state" -f context=ios-gate -f description="$description" -f target_url="$run_url" >/dev/null
