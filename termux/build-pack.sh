#!/data/data/com.termux/files/usr/bin/bash
# Builds the offline research pack on GitHub, then rebuilds the app with it inside.
# Takes about 1-3 hours the first time. You can close Termux; GitHub keeps working.
# Usage:  bash termux/build-pack.sh
set -e
echo "Starting the offline research pack on GitHub..."
gh workflow run offline-pack.yml
sleep 15
RUN_ID=$(gh run list --workflow offline-pack.yml --limit 1 --json databaseId --jq '.[0].databaseId')
echo "Pack build $RUN_ID is running. Waiting (this can take 1-3 hours)..."
gh run watch "$RUN_ID" --exit-status --interval 60 || {
  echo "The pack build failed. See why with:  gh run view $RUN_ID --log-failed"
  exit 1
}
echo "Pack ready. Building the app with the pack inside..."
gh workflow run build.yml
sleep 15
echo "Now run:  bash termux/get-apk.sh"
