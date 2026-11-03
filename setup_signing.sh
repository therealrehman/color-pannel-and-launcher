#!/usr/bin/env bash
# One command that gives THIS GitHub repo its signing key (Termux):
#   bash setup_signing.sh              (the repo is read from this folder's git remote)
#   bash setup_signing.sh owner/repo   (or name it)
# It (1) makes your signing key once or re-uses the one you already have, (2) installs the GitHub CLI (gh) and logs you in (a code you type
# in the browser), (3) puts the 4 secrets into the repo for you, (4) deletes the temporary secrets file. Secrets are per repository: a new
# repo has none, which is why "Build signed release APK" said "A signing secret is missing".
cd "$(dirname "$0")" || exit 1

REPO="$1"
if [ -z "$REPO" ]; then
  URL=$(git remote get-url origin 2>/dev/null)
  if [ -n "$URL" ]; then
    URL="${URL%.git}"
    REPO=$(echo "$URL" | awk -F'[/:]' '{print $(NF-1)"/"$NF}')
  fi
fi
if [ -z "$REPO" ]; then
  printf "GitHub repo (owner/name, for example therealrehman/color-pannel-and-launcher): "
  read -r REPO
fi
case "$REPO" in */*) ;; *) echo "That is not owner/name."; exit 1;; esac
echo "Repo: $REPO"

# 1. the key (re-uses ~/oneui9-release.jks when it exists; writes the 4 values to Downloads)
OK_SIGNING_AUTO=1 bash make_signing_key.sh || exit 1
DL="$HOME/storage/downloads"; [ -d "$DL" ] || DL="$HOME/storage/shared/Download"
TXT="$DL/OkLauncher-github-secrets.txt"
[ -f "$TXT" ] || { echo "The secrets file was not made ($TXT)."; exit 1; }

# 2. the GitHub CLI
if ! command -v gh >/dev/null 2>&1; then
  echo "Installing the GitHub CLI (gh)..."
  pkg install -y gh || { echo "Could not install gh. Add the 4 secrets by hand from $TXT (see SIGNING.md)."; exit 1; }
fi
if ! gh auth status >/dev/null 2>&1; then
  echo
  echo "Log in to GitHub: gh shows a code and an address. Open the address in your browser, type the code, allow it, then come back here."
  gh auth login --hostname github.com --git-protocol https --web --scopes repo,workflow || exit 1
fi

# 3. the secrets
ok=0
while IFS= read -r line; do
  case "$line" in
    RELEASE_*:*)
      name="${line%%:*}"
      value="${line#*:}"
      if printf '%s' "$value" | gh secret set "$name" --repo "$REPO" >/dev/null 2>&1; then
        echo "  set: $name"
        ok=$((ok + 1))
      else
        echo "  FAILED: $name (does the repo exist, and is it yours?)"
      fi
      ;;
  esac
done < "$TXT"
if [ "$ok" -ne 4 ]; then
  echo "Only $ok of 4 secrets were set. The file is kept: $TXT (add the rest by hand, see SIGNING.md)."
  exit 1
fi
rm -f "$TXT"
echo
gh secret list --repo "$REPO" 2>/dev/null
echo
echo "Done: 4 secrets are in $REPO. The secrets file was deleted."
echo "BACK UP $DL/oneui9-release.jks (cloud drive / another phone): without it the app can never be updated."
printf "Build the signed APK now? (y/n) "
read -r ans
case "$ans" in
  y|Y) gh workflow run release.yml --repo "$REPO" && echo "Started. In about 5 minutes: GitHub > Actions > Build signed release APK > artifact OkLauncher-release-apk." ;;
  *) echo "Later: GitHub > Actions > Build signed release APK > Run workflow." ;;
esac
