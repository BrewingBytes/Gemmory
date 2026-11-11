#!/usr/bin/env bash
# Posts the `ios-gate` commit status on a PR's head commit (T60, eng D25). `ios-gate` is the required check on main,
# not the macOS job itself, so a PR labelled `android-hotfix` can merge on ci.yml alone while the iOS build still runs
# and reports (premise 2: iOS never blocks Android).
#
#   ios-gate.sh <pr number> <head sha> [ios job result]
#
# With the label: success at once. Without it: the `ios` job's result when given (success, failure), or else the
# latest `ios` check run on the commit (pending while it runs). The label is read live, so adding or removing it while
# the build runs is honoured by whichever of the two callers posts last.
#
# A `skipped` check run is never a verdict: it is the `ios` job of a label-change run, which never builds. A `cancelled`
# job is looked up: if a newer `ios` run exists on the commit (a reopen or "Re-run all jobs" replaced it), that run
# decides; if it is still the newest, it hit the job's time limit or was stopped by hand, and that is a failure.
# ios-gate-test.sh (next to this file, run by ci.yml) covers every branch with a stub `gh`.
set -euo pipefail

pr=$1
sha=$2
result=${3:-}
repo=$GITHUB_REPOSITORY
run_url="${GITHUB_SERVER_URL}/${repo}/actions/runs/${GITHUB_RUN_ID}"

labels=$(gh pr view "$pr" --repo "$repo" --json labels --jq '.labels[].name')
if grep -qx 'android-hotfix' <<<"$labels"; then
  state=success
  description='android-hotfix: merges on ci.yml alone; the iOS build still runs and reports'
else
  # A cancelled job's own result can't tell a time-out from being replaced; the check runs on the commit can.
  [ "$result" = cancelled ] && result=
  if [ -z "$result" ]; then
    run=$(gh api "repos/$repo/commits/$sha/check-runs?check_name=ios&filter=all" \
      --jq '.check_runs | map(select(.conclusion != "skipped")) | sort_by(.started_at) | last | "\(.status) \(.conclusion)"')
    case "$run" in
      'completed success') result=success ;;
      'completed cancelled') result='cancelled or timed out' ;;
      completed\ *) result=${run#completed } ;;
      *) result=pending ;;
    esac
  fi
  case "$result" in
    success) state=success description='iOS build and tests passed' ;;
    pending) state=pending description='Waiting for the iOS build (ios job)' ;;
    *) state=failure description="iOS build or tests: $result" ;;
  esac
fi

echo "ios-gate on ${sha::7}: $state ($description)"
gh api --method POST "repos/$repo/statuses/$sha" \
  -f state="$state" -f context=ios-gate -f description="$description" -f target_url="$run_url" >/dev/null
