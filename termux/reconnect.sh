#!/data/data/com.termux/files/usr/bin/bash
# Reconnect this folder to your GitHub repository if git says "not a git repository".
# Usage:  bash termux/reconnect.sh
set -e
REPO_URL="https://github.com/NennRoungkh07168/MENK.git"
if [ -d .git ]; then
  echo "Already connected to: $(git remote get-url origin 2>/dev/null || echo 'no remote')"
  exit 0
fi
git init -b main
git remote add origin "$REPO_URL"
git fetch origin
git reset --soft origin/main
echo "Reconnected. Now run:  bash termux/push.sh \"your message\""
