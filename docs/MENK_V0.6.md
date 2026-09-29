# MENK v0.6 — unified workspace

Built from v0.5.1 (live search, offline library, gold logo launcher icon) plus the
v0.6 feature list, cleaned up and connected.

## Navigation
Home · Explore · Research · Lab · Evidence

## Explore
- Drug index: 51 generic medicines with pharmacology families; tap for live research
- Offline library (works without internet), 56 entries in 7 sections:
  medicines (10), diseases (8), herbs & plants (5), biochemistry (5),
  pharma companies (8), research institutions & labs (15), medical devices (5)
- Compound lookup: formula, PubChem 2D structure drawing, computed properties
- Link to the Simulation Lab

## Research center
Search box for all live sources, plus shortcuts to papers (Europe PMC/PubMed),
clinical trials with posted results (ClinicalTrials.gov), medicine records
(openFDA: approvals, manufacturers, recalls, labels, interactions), chemistry
(PubChem), companies and institutions.

## Simulation Lab (educational only — not doses or treatment advice)
- First-order decay: C(t) = C0·e^(−kt), half-life ln2/k, with a live curve
- Receptor occupancy: C / (C + Kd), log-scale curve
- Hill dose-response: Cⁿ / (Cⁿ + EC50ⁿ), log-scale curve

## Changes from the uploaded v0.6.0 draft
- The offline library is reachable again (the draft replaced it and removed its only entry points)
- Drug index opens as a proper Explore section instead of a hidden search keyword
- Companies, devices and biochemistry sections now contain real entries instead of generic searches
- Kinetics model is a real first-order decay with half-life, not rate = k × C
- Simulation curves are drawn; tabs match the app's style; screens fit tablets
- User-facing name is MENK throughout

## Planned next (one per update)
CameraX + ML Kit barcode scanning · periodic table · 3D molecule viewer ·
structure drawing and similarity search · reaction viewer · MENK server data
