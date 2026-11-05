#!/usr/bin/env bash
# Tests check-release-gate.sh without the network: a fake `gh` on PATH answers `gh api` from
# JSON fixtures (applying the real --jq expression with jq), and a scratch git repo stands in
# for the checkout. Run by CI on every PR; run locally with `bash .github/scripts/test-release-gate.sh`.
set -euo pipefail

gate="$(cd "$(dirname "$0")" && pwd)/check-release-gate.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Fake gh: `gh api <path> --jq <expr>`. CI workflow runs come from $RUNS_FIXTURE. Any check-runs
# query gets the Release workflow's own in-progress `build` job, the case that broke the first
# version of the gate, so a gate that reads check runs fails these tests.
mkdir "$work/bin"
cat > "$work/bin/gh" <<'GH'
#!/usr/bin/env bash
set -euo pipefail
[ "$1" = api ] || { echo "fake gh: only 'api' is supported" >&2; exit 2; }
path="$2"; shift 2
expr="."
while [ $# -gt 0 ]; do
  case "$1" in
    --jq) expr="$2"; shift 2 ;;
    *) shift ;;
  esac
done
echo "$path" >> "$GH_LOG"
case "$path" in
  */actions/workflows/ci.yml/runs\?*) jq -r "$expr" "$RUNS_FIXTURE" ;;
  */check-runs*) jq -r "$expr" "$OWN_CHECK_FIXTURE" ;;
  *) echo "fake gh: unexpected path $path" >&2; exit 2 ;;
esac
GH
chmod +x "$work/bin/gh"

cat > "$work/own-check.json" <<'JSON'
{"check_runs": [
  {"name": "build", "app": {"slug": "github-actions"}, "status": "completed", "conclusion": "success",
   "started_at": "2026-11-05T03:44:56Z", "html_url": "https://github.com/o/r/actions/runs/100/job/1"},
  {"name": "build", "app": {"slug": "github-actions"}, "status": "in_progress", "conclusion": null,
   "started_at": "2026-11-05T05:00:00Z", "html_url": "https://github.com/o/r/actions/runs/200/job/2"}
]}
JSON

# Scratch repo: one commit on main, one commit off it.
repo="$work/repo"
git init -q "$repo"
git -C "$repo" -c user.name=t -c user.email=t@example.invalid commit -q --allow-empty -m main
on_main="$(git -C "$repo" rev-parse HEAD)"
git -C "$repo" update-ref refs/remotes/origin/main "$on_main"
git -C "$repo" -c user.name=t -c user.email=t@example.invalid commit -q --allow-empty -m side
off_main="$(git -C "$repo" rev-parse HEAD)"

run_json() { # status conclusion created_at
  printf '{"status":"%s","conclusion":%s,"created_at":"%s","html_url":"https://github.com/o/r/actions/runs/1"}' \
    "$1" "$( [ "$2" = null ] && echo null || echo "\"$2\"")" "$3"
}

failures=0
# case <name> <expected exit> <sha> <runs json array>
case_() {
  local name="$1" want="$2" sha="$3" runs="$4" got=0
  printf '{"total_count":0,"workflow_runs":%s}' "$runs" > "$work/runs.json"
  : > "$work/gh.log"
  (
    cd "$repo"
    PATH="$work/bin:$PATH" GH_LOG="$work/gh.log" RUNS_FIXTURE="$work/runs.json" \
      OWN_CHECK_FIXTURE="$work/own-check.json" GITHUB_SHA="$sha" GITHUB_REF_NAME=v9.9.9 \
      GITHUB_REPOSITORY=o/r GITHUB_RUN_ID=200 bash "$gate"
  ) > "$work/out.txt" 2>&1 || got=$?
  if [ "$got" = "$want" ]; then
    echo "ok   $name"
  else
    echo "FAIL $name: exit $got, want $want"; sed 's/^/     /' "$work/out.txt"
    failures=$((failures + 1))
  fi
}

ok_run="[$(run_json completed success 2026-11-05T03:44:50Z)]"

case_ "CI passed on main while this release run's own build job is in progress" 0 "$on_main" "$ok_run"
if ! grep -q '/actions/workflows/ci.yml/runs?.*head_sha='"$on_main" "$work/gh.log" \
   || ! grep -q 'event=push' "$work/gh.log" || ! grep -q 'branch=main' "$work/gh.log"; then
  echo "FAIL query asks for ci.yml push runs on main for the tagged commit"; cat "$work/gh.log"
  failures=$((failures + 1))
else
  echo "ok   query asks for ci.yml push runs on main for the tagged commit"
fi
case_ "commit not on main" 1 "$off_main" "$ok_run"
case_ "no CI run for the commit" 1 "$on_main" "[]"
case_ "CI still running" 1 "$on_main" "[$(run_json in_progress null 2026-11-05T03:44:50Z)]"
case_ "CI failed" 1 "$on_main" "[$(run_json completed failure 2026-11-05T03:44:50Z)]"
case_ "CI cancelled" 1 "$on_main" "[$(run_json completed cancelled 2026-11-05T03:44:50Z)]"
case_ "an earlier run failed, the latest passed" 0 "$on_main" \
  "[$(run_json completed failure 2026-11-05T03:00:00Z),$(run_json completed success 2026-11-05T03:44:50Z)]"
case_ "an earlier run passed, the latest failed" 1 "$on_main" \
  "[$(run_json completed failure 2026-11-05T03:44:50Z),$(run_json completed success 2026-11-05T03:00:00Z)]"

if [ "$failures" -ne 0 ]; then
  echo "$failures release gate test(s) failed"
  exit 1
fi
echo "all release gate tests passed"
