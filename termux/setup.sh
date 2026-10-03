#!/data/data/com.termux/files/usr/bin/bash
# One-time setup: installs git + GitHub CLI, signs in, creates the GitHub repo and pushes.
# Run from the MEKN folder:  bash termux/setup.sh
set -e

pkg update -y
pkg install -y git gh

# Lets Termux save the downloaded APK to your phone's Downloads folder
termux-setup-storage || true

if [ -z "$(git config --global user.name)" ]; then
  read -rp "Your name for git commits: " NAME
  git config --global user.name "$NAME"
fi
if [ -z "$(git config --global user.email)" ]; then
  read -rp "Your email for git commits: " EMAIL
  git config --global user.email "$EMAIL"
fi

gh auth status >/dev/null 2>&1 || gh auth login

if [ ! -d .git ]; then
  git init -b main
  git add -A
  git commit -m "RENK v0.1 - Home screen"
fi

read -rp "GitHub repo name [mekn]: " REPO
REPO=${REPO:-mekn}
gh repo create "$REPO" --private --source=. --push

echo
echo "Done. GitHub is now building the APK."
echo "In a few minutes, run:  bash termux/get-apk.sh"
