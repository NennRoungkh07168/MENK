# RENK — Research Evidence Network of Knowledge (Android app, v1.01)

RENK explains published evidence. It does not diagnose or recommend treatment.

v0.2 adds live search. Type a disease, medicine, herb or compound on Home and
press Search. Results come from free public databases:

- Europe PMC (includes PubMed): research papers, abstracts, and counts by study type
- ClinicalTrials.gov: registered clinical studies and how many are recruiting
- PubChem: chemical identity (formula, weight, identifiers)
- openFDA: official US drug label (approved uses, interactions, warnings),
  FDA approvals (Drugs@FDA), products and manufacturers (NDC directory),
  and manufacturing recalls (enforcement reports)
- ClinicalTrials.gov posted results: enrollment, main outcome, serious adverse events

The app (RENK) shows what has been studied and how strong that research is.
It does not diagnose, recommend treatment, or claim that anything is a cure.

v0.5 adds the built-in RENK Library (works offline): 10 medicines, 8 diseases,
5 herbs and 15 research institutions in Germany and India. Every section is
tagged by evidence type (Official, Guideline, Human research, Lab only,
Traditional, Safety). Data lives in app/src/main/assets/library.json.

The Scan tab is still a placeholder.

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
run "Build MEKN APK", and download `renk-debug-apk` from the finished run.

## Project layout

    app/src/main/java/org/mekn/app/MainActivity.kt   the app
    app/build.gradle.kts                             app settings and libraries
    .github/workflows/build.yml                      GitHub build instructions
    termux/                                          phone helper scripts

## Notes

- This is a debug build, fine for testing on your own phone. Publishing on the
  Play Store later needs a signed release build.
- If a build fails, run:  gh run view --log-failed

Note: the app is named RENK. Internal code names (folder MEKN, package org.mekn.app)
stay unchanged so updates keep installing over the existing app.

## Database design

- docs/DATABASE.md — architecture for 100M+ compounds and millions of medicines
- database/schema.sql — full PostgreSQL schema (server side, not used by the app yet)
- database/chem_search_rdkit.sql — optional structure and similarity search

## v0.6
See docs/RENK_V0.6.md for the five-tab layout, drug index, library sections, Research center and Simulation Lab.

## v0.7
Side effects, prescribing information, PDF reports, offline search, country filters and
international institutions. See docs/RENK_V0.7.md.

If git says "not a git repository", run:  bash termux/reconnect.sh

## v1.01
Renamed to RENK. Evidence PDF library (offline), brand-name chemistry, estimated levels for new papers. See docs/RENK_V1.01.md.
