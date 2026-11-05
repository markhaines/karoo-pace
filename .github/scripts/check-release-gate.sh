#!/usr/bin/env bash
# Release gate, run first by .github/workflows/release.yml: the tagged commit must be on main,
# and the latest CI workflow run (ci.yml, pushed to main) for that exact commit must have passed.
#
# It asks the Actions API for ci.yml's runs, not for check runs by name: the Release workflow's
# own jobs are check runs on the same commit and still in progress while this runs, so a
# name-based query would see them instead of CI. Tested by test-release-gate.sh.
#
# Needs: GITHUB_SHA, GITHUB_REF_NAME, GITHUB_REPOSITORY, a token for gh with actions: read,
# and a checkout with full history (origin/main present).
set -euo pipefail

sha="${GITHUB_SHA:?}"
tag="${GITHUB_REF_NAME:?}"
repo="${GITHUB_REPOSITORY:?}"

if ! git merge-base --is-ancestor "$sha" origin/main; then
  echo "::error::$tag ($sha) is not on main. Tag a commit on main."
  exit 1
fi

latest=$(gh api "repos/$repo/actions/workflows/ci.yml/runs?head_sha=$sha&event=push&branch=main&per_page=100" \
  --jq '.workflow_runs | sort_by(.created_at) | last
        | if . == null then "missing" else "\(.status)/\(.conclusion // "none") \(.html_url)" end')

case "$latest" in
  completed/success\ *)
    echo "CI passed on $sha: ${latest#* }"
    ;;
  missing)
    echo "::error::No CI run on main for $sha. Merge to main, let CI run, then tag."
    exit 1
    ;;
  completed/*)
    echo "::error::CI on main for $sha ended '${latest%% *}': ${latest#* }"
    exit 1
    ;;
  *)
    echo "::error::CI on main for $sha is '${latest%% *}', not finished. Re-run this workflow once it passes: ${latest#* }"
    exit 1
    ;;
esac
