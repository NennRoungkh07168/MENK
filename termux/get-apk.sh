#!/data/data/com.termux/files/usr/bin/bash
# Wait for the latest GitHub build, download the APK and open the installer.
# Usage:  bash termux/get-apk.sh
set -e

RUN_ID=$(gh run list --workflow build.yml --limit 1 --json databaseId --jq '.[0].databaseId')
if [ -z "$RUN_ID" ]; then
  echo "No builds found yet. Push some code first: bash termux/push.sh"
  exit 1
fi

echo "Waiting for build $RUN_ID to finish..."
gh run watch "$RUN_ID" --exit-status || {
  echo "The build failed. See the error with:  gh run view $RUN_ID --log-failed"
  exit 1
}

OUT="$HOME/storage/downloads/menk"
[ -d "$HOME/storage/downloads" ] || OUT="$HOME/mekn-apk"
rm -rf "$OUT"
gh run download "$RUN_ID" --name menk-debug-apk --dir "$OUT"

APK=$(ls "$OUT"/*.apk | head -n 1)
echo "APK saved to: $APK"
termux-open "$APK" || echo "Open it from your file manager to install."
