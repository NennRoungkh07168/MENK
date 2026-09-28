# MEKN — Medical Evidence Knowledge Network (Android app, v0.1)

MEKN explains published evidence. It does not diagnose or recommend treatment.

This version contains the Home screen (search, camera button, Patient/Researcher
switch, Scan card, Explore cards, Recent scans) and bottom navigation.
Explore, India, Evidence and Scan are placeholders for now.

## How the build works

You edit and push from your phone (Termux). GitHub Actions builds the APK on
GitHub's servers. You download the APK back to your phone and install it.
Building directly inside Termux is not supported, because Android's build tools
expect a desktop computer.

## First-time setup in Termux

1. Install Termux from F-Droid or GitHub (the Play Store version is outdated).
2. Copy the zip into Termux's home folder and unzip it there:

       termux-setup-storage
       pkg install -y unzip
       cp ~/storage/downloads/MEKN.zip ~
       cd ~ && unzip MEKN.zip && cd MEKN

3. Run the setup script. It installs git and the GitHub CLI, signs you in to
   GitHub, creates a private repo and pushes the code:

       bash termux/setup.sh

4. Wait a few minutes, then download and install the APK:

       bash termux/get-apk.sh

   The first time, Android will ask you to allow Termux to install apps.

## Everyday use

    bash termux/push.sh "describe your change"   # send changes, start a build
    bash termux/get-apk.sh                       # wait, download, install

## Without Termux

Upload the files to a GitHub repo through the website, open the Actions tab,
run "Build MEKN APK", and download `mekn-debug-apk` from the finished run.

## Project layout

    app/src/main/java/org/mekn/app/MainActivity.kt   the app
    app/build.gradle.kts                             app settings and libraries
    .github/workflows/build.yml                      GitHub build instructions
    termux/                                          phone helper scripts

## Notes

- This is a debug build, fine for testing on your own phone. Publishing on the
  Play Store later needs a signed release build.
- If a build fails, run:  gh run view --log-failed
