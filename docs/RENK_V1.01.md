# RENK v1.01 — Research Evidence Network of Knowledge

## 1. Search results as PDF files
- Every search has a "Get these results as a PDF" card at the top of Summary:
  - Download PDF: saves the full report where you choose (e.g. Downloads) to read or share
  - Keep in RENK: saves it into the Evidence tab's PDF library
- The report covers every tab: research counts, papers, trials with results, side effects,
  official prescribing information, approvals, manufacturers, recalls and chemistry
- Library pages have a PDF button too
- Evidence tab = PDF library: open inside the app with no internet, save a copy, delete

## 2. Offline research pack (research inside the app)
GitHub runs RENK's own search for every topic in app/offline_topics.txt (120 topics: the drug
index, library medicines, herbs and 55 common diseases), saves every answer, and builds them into
the APK. On first launch the app unpacks them. Those topics then work fully offline: Summary,
Papers, Trials, Medicine (side effects, prescribing, approvals) and Chemistry, plus PDF reports.

How to build it (once, then it refreshes automatically every month):
1. Optional but recommended: get a free openFDA API key at open.fda.gov/apis/authentication,
   then on GitHub: Settings → Secrets and variables → Actions → New repository secret,
   name OPENFDA_API_KEY. Without it, FDA data may be incomplete for some topics.
2. In Termux: bash termux/build-pack.sh   (or GitHub → Actions → "Build offline research pack" → Run)
3. When it finishes (1-3 hours): bash termux/get-apk.sh and install.

To add topics: edit app/offline_topics.txt, push, and run the pack build again.
Size guide: ~120 topics ≈ 50-150 MB app; more topics = bigger app.

## 3. Prescribing and side effects
Guides: How prescriptions work · Good prescribing (WHO 6 steps) · Understanding side effects ·
Dose basics. Medicine tab: label side effects, FDA side-effect reports, official prescribing info.

## Limits
- The whole of world research (tens of millions of papers) cannot fit on a device. The pack holds
  the most relevant results for each chosen topic; new topics still need internet once.
- University course material (e.g. Harvard's) is copyrighted and can't be bundled. Openly
  licensed textbooks could be added later where the license allows.
