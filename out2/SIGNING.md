# Signed release APK - how the key gets into GitHub

The workflow **Build signed release APK** signs with a key that lives in four *repository secrets*. Secrets belong to ONE repository, so a new
repo (a copy, a rename, a second project) starts with none. That is the error "A signing secret is missing or empty".

## The easy way (Termux, once per repo)
```
cd ~/<this project folder>
bash setup_signing.sh
```
It makes your key (or re-uses `~/oneui9-release.jks`), installs `gh`, asks you to log in to GitHub with a code in the browser, sets the 4 secrets
(`RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) in the repo, deletes the temporary file and can start the build.
Use the SAME key for the same app: that is what lets updates install over the old version and keeps the store account happy.

## Without gh (by hand)
`bash make_signing_key.sh` writes `Downloads/OkLauncher-github-secrets.txt`. In GitHub: the repo > Settings > Secrets and variables > Actions > New repository secret.
Name = the word before the colon, Secret = what follows it. Delete the txt afterwards.

## What the workflow does now when a secret is missing
It no longer stops. It makes a TEMPORARY key for that run, so you still get a release (shrunk) APK to test; the artifact is named
`OkLauncher-release-apk-TEMP-KEY-do-not-publish` and the run shows a yellow warning. Never publish that one: each run uses a different key.
With the 4 secrets the artifact is called `OkLauncher-release-apk` and is the one for the store.

## Keep safe
Back up `oneui9-release.jks` (it is in Downloads after the script and in Termux's home). Never post the keystore, the passwords or the secrets file anywhere.
