#!/data/data/com.termux/files/usr/bin/bash
# Save your changes and send them to GitHub (this starts a new APK build).
# Usage:  bash termux/push.sh "what you changed"
set -e
if [ ! -d .git ]; then
  echo "This folder isn't connected to GitHub. Run:  bash termux/reconnect.sh"
  exit 1
fi
git add -A
git commit -m "${1:-Update}" || echo "Nothing new to commit."
git push
echo "Pushed. Run  bash termux/get-apk.sh  to fetch the new APK."
