#!/usr/bin/env bash
# Writes the text of a release page, in the style of Streak's releases: a big heading, then the "what's new" sections
# from CHANGELOG.md that were added since the last full release, then how to download.
#
# Usage: release-notes.sh <build number> <branch> <commit sha> <repo (owner/name)> <previous release commit or empty>
# Output: the release text on stdout.
set -euo pipefail

build="$1"; branch="$2"; sha="$3"; repo="$4"; prev="${5:-}"
changelog="CHANGELOG.md"

# The titles of the sections in the changelog, newest first.
titles() { sed -n 's/^## //p' "$changelog"; }

# The sections added since the last full release. If that cannot be worked out, only the newest section is shown.
wanted=""
# (If the changelog did not exist yet at the last release, only the newest section is shown, not the whole history.)
if [ -n "$prev" ] && git cat-file -e "${prev}^{commit}" 2>/dev/null && git cat-file -e "${prev}:${changelog}" 2>/dev/null; then
  wanted="$(git diff -U0 "$prev" "$sha" -- "$changelog" | sed -n 's/^+## //p' || true)"
fi
[ -n "$wanted" ] || wanted="$(titles | head -n 1)"

# One section: its heading and everything up to the next "## " heading.
section() {
  awk -v want="## $1" '
    $0 == want { on = 1; print; next }
    on && /^## / { exit }
    on { print }
  ' "$changelog"
}

short="${sha:0:7}"
if [ "$branch" = "main" ]; then
  echo "# Life Tracker build $build"
else
  echo "# Test build $build"
  echo
  echo "> 🧪 A test build of the branch \`$branch\`. It is not merged yet, so it is not the latest version."
fi
echo

while IFS= read -r title; do
  [ -n "$title" ] || continue
  section "$title"
  echo
done <<< "$(titles | while IFS= read -r t; do printf '%s\n' "$wanted" | grep -Fxq -- "$t" && printf '%s\n' "$t"; done)"

echo "---"
echo
echo "## ⬇️ Download"
echo
echo "Get **life-tracker.apk** from the files below (under **Assets**) and open it on your phone. It installs over your old version and keeps all your data."
echo
echo "Android may ask once to allow installs from your browser or Files app. Google Play Protect may warn that the app is from outside the Play Store. Both are expected for your own app."
echo
if [ -n "$prev" ] && git cat-file -e "${prev}^{commit}" 2>/dev/null; then
  echo "Commit \`$short\` · [Everything that changed since the last release](https://github.com/$repo/compare/${prev:0:12}...$short)"
else
  echo "Commit [\`$short\`](https://github.com/$repo/commit/$sha)"
fi
