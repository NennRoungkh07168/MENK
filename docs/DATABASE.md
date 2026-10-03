# RENK Knowledge Database — Architecture

Design for a RENK database that can hold **100+ million chemical records**, **millions of
medicine products**, and tens of millions of publications, trials and bioactivity
measurements, with every fact traceable to its source.

The full table definitions are in [`database/schema.sql`](../database/schema.sql)
(PostgreSQL 15+). Optional chemical-structure search is in
[`database/chem_search_rdkit.sql`](../database/chem_search_rdkit.sql).

---

## 1. The key decision: where the data lives

A phone or tablet cannot hold 100 million compounds. A full PubChem-scale chemistry
database with names, structures and indexes runs to hundreds of gigabytes. So RENK uses
three tiers:

```
┌──────────────────────────────────────────────────────────────┐
│ ANDROID APP (Kotlin, Jetpack Compose)                        │
│  • Offline pack: core medicines, diseases, herbs (few MB)    │
│  • Local cache of pages the user opened (Room / SQLite)      │
│  • Camera scan → barcode / text → lookup                     │
└───────────────┬──────────────────────────────────────────────┘
                │ HTTPS / JSON  (search, pages, evidence, scan)
┌───────────────▼──────────────────────────────────────────────┐
│ RENK API SERVER                                              │
│  • Search, entity pages, evidence, "Why do we believe this?" │
│  • Caching layer for hot records                             │
└───────┬──────────────────┬──────────────────┬────────────────┘
        │                  │                  │
┌───────▼───────┐  ┌───────▼───────┐  ┌───────▼────────────────┐
│ PostgreSQL    │  │ Search index  │  │ Structure search       │
│ all records,  │  │ names, synonyms│ │ RDKit cartridge:       │
│ links,        │  │ autocomplete, │  │ substructure and       │
│ evidence      │  │ typo-tolerant │  │ similarity             │
└───────▲───────┘  └───────▲───────┘  └───────▲────────────────┘
        │                  │                  │
┌───────┴──────────────────┴──────────────────┴────────────────┐
│ INGESTION PIPELINE (scheduled jobs)                          │
│  download → validate → normalize → match identifiers →       │
│  deduplicate → load → extract evidence → re-index            │
└──────────────────────────────────────────────────────────────┘
        ▲ PubChem · ChEMBL · openFDA · DailyMed · RxNorm · GSRS/UNII
        ▲ ClinicalTrials.gov · DRKS · CTRI · EU CTIS · Europe PMC
        ▲ UniProt · Reactome · Rhea · Orphanet · IMPPAT · ROR · GLEIF
```

**Today's app (v0.5)** skips the server and calls public APIs directly. That keeps it free
and is the right choice until RENK needs things those APIs can't do: cross-linking
sources, structure search, its own evidence grading, and offline use.

---

## 2. The data model

### 2.1 One global id for everything

Every node — compound, substance, ingredient, product, disease, target, pathway,
organization, trial, publication, reaction, organism, assay — first gets a row in
**`entity`**, and its own table uses that same id. This gives:

- **Real foreign keys for relationships that can point at anything** (evidence claims,
  interactions, trial interventions, paper mentions). This fixes the "polymorphic
  reference" weakness in the earlier draft schema.
- **One `identifier` table for all external ids:** CAS, PubChem CID, ChEMBL, UNII,
  RxCUI, ATC, NDC, GTIN barcode, ICD-10/11, MeSH, Orphanet, UniProt, ROR, LEI, NCT,
  DRKS, CTRI, PMID, DOI and more. Looking up any code takes one indexed query.

### 2.2 Chemistry → substance → ingredient → product

These four are deliberately separate, because they are different things:

| Layer | Example | Table |
|---|---|---|
| **Compound** — one exact structure | acetylsalicylic acid, InChIKey `BSYNRYMUTXBXSQ-…` | `compound` |
| **Substance** — may have no single structure | insulin glargine, turmeric extract, a mixture | `substance` |
| **Ingredient** — the active part of medicines | aspirin (INN), linked to its substance | `ingredient` |
| **Product** — one brand/form in one country | "Brand X 75 mg tablets", India | `product` |

A product links to its ingredients with strengths (`product_ingredient`), to its
packages and barcodes (`package`), its maker (`organization`), its manufacturing sites
(`manufacturing_site`), its regulatory decisions per country (`marketing_authorization`),
its recalls (`recall`) and its official labels (`label_document`, `label_section`).

### 2.3 Tables by area

| Area | Tables |
|---|---|
| Provenance | `source`, `source_release`, `ingest_run`, `source_record` |
| Backbone | `entity`, `entity_kind`, `identifier`, `id_namespace` |
| Chemistry | `compound`, `compound_inchikey`, `compound_name`, `compound_structure`, `compound_property`, `property_def`, `compound_hazard`, `toxicity`, `substance`, `substance_component` |
| Organizations | `organization`, `country` |
| Medicines | `ingredient`, `drug_class`, `ingredient_class`, `product`, `product_ingredient`, `package`, `manufacturing_site`, `product_site`, `marketing_authorization`, `recall`, `label_document`, `label_section` |
| Biology | `disease`, `target`, `pathway`, `target_pathway`, `assay`, `bioactivity`, `mechanism` |
| Plants | `organism`, `natural_occurrence`, `traditional_use` |
| Reactions | `reaction`, `reaction_participant` |
| Research | `publication`, `entity_mention`, `clinical_trial`, `trial_intervention`, `trial_condition`, `trial_site`, `trial_outcome` |
| Clinical facts | `indication`, `interaction`, `adverse_effect` |
| Evidence | `evidence_claim`, `evidence_level`, `predicate` |

### 2.4 Evidence: the heart of RENK

Every claim is a row in **`evidence_claim`**:

```
subject  ──predicate──►  object        e.g. metformin  STUDIED_FOR  type 2 diabetes
level (1–8) · direction (benefit / no effect / harm) · species · population
sample size · summary · limitations · publication · trial · source · retrieved_at
```

The eight levels run from **L1** (regulator / official label) to **L8** (hypothesis or
traditional use). Levels are never merged into one score. The app's **"Why?"** button
reads one claim row and its source.

---

## 3. How it handles 100+ million rows

- **Hash partitioning.** The largest tables (`compound`, `compound_name`,
  `compound_structure`, `compound_property`, `identifier`, `bioactivity`,
  `entity_mention`, `evidence_claim`, `source_record`) are split into 32–64 partitions,
  so each piece loads, indexes and vacuums independently and in parallel.
- **A narrow main table.** `compound` holds only the fields used in lists and filters.
  Structure files, synonyms and rare properties live in side tables, so the hot data
  stays in memory.
- **Global deduplication by InChIKey** through `compound_inchikey`. A partitioned table
  can't enforce uniqueness on a non-partition column, so this lookup table does it.
- **Bulk loading.** Create partitions, `COPY` data in, then build indexes and validate
  foreign keys (`NOT VALID` → `VALIDATE`). Row-by-row inserts would take weeks.
- **Incremental updates.** `source_record.content_hash` lets each nightly run skip
  records that haven't changed.
- **Keyset pagination** in the API (`WHERE id > last_seen`), never `OFFSET` on huge tables.
- **Search outside PostgreSQL** for names: a search engine (OpenSearch or similar) holds
  names and synonyms for fast, typo-tolerant autocomplete. PostgreSQL stays the source of
  truth; the index is rebuilt from it.
- **Structure search** with the RDKit cartridge: Morgan fingerprints plus GiST indexes
  make "find similar molecules" and "find molecules containing this ring" practical at
  this scale.

**Sizing guide.** Expect hundreds of gigabytes to low terabytes once chemistry, names,
bioactivity and literature links are fully loaded. Measure with a pilot load of
1–5 million compounds before choosing hardware.

---

## 4. Sources and licenses

Every source gets a row in `source` with its license terms, and every fact keeps its
`source_id`. Some examples:

| Source | Provides | License notes |
|---|---|---|
| PubChem | Compounds, names, properties, hazards | Public domain (US Gov); some depositor data has terms |
| ChEMBL | Bioactivity, mechanisms, targets | CC BY-SA — share-alike applies to redistributed data |
| openFDA / DailyMed | Labels, approvals, NDC products, recalls | Public domain (US Gov) |
| RxNorm | US drug names and codes | Free; some embedded vocabularies need a UMLS license |
| GSRS / UNII | Substance definitions incl. biologics | Public (FDA) |
| ClinicalTrials.gov | Trials and posted results | Public domain |
| DRKS, CTRI, EU CTIS | National and EU trials | Public registries; check reuse terms |
| Europe PMC / PubMed | Publications, annotations | Metadata free; full text depends on article license |
| UniProt, Reactome, Rhea | Proteins, pathways, biochemical reactions | CC BY 4.0 / CC0 |
| Orphanet | Rare diseases | CC BY 4.0 |
| IMPPAT | Indian medicinal plants and phytochemicals | Check terms before bulk use |
| ROR, GLEIF | Institutions, companies | CC0 |

**Limits of free data:**

- **CAS Registry Numbers:** the full CAS Registry is a licensed product. RENK can store CAS
  numbers only as they appear in public sources (PubChem, FDA). Complete CAS coverage
  would need a license.
- **Reactions:** most of the "hundreds of millions" of documented reactions sit in
  commercial databases. Open sources cover biochemical reactions (Rhea) and a few million
  patent reactions. RENK keeps reactions at the educational and biochemical level, not
  step-by-step synthesis procedures.

---

## 5. Android architecture

```
UI (Jetpack Compose)          Home · Library · Search · Evidence · Scan
   │
ViewModels                     screen state, paging
   │
Repositories                   decide: offline pack → local cache → server/public API
   │
 ┌─┴──────────────┬───────────────────┬────────────────────┐
Room (SQLite)    Retrofit (HTTPS)     CameraX + ML Kit      WorkManager
offline pack,    RENK API or public   barcode + text        weekly offline-pack
cache, saved     APIs                 recognition           and cache refresh
```

**Local tables (Room):**

| Table | Purpose |
|---|---|
| `offline_entry` (+ full-text index) | Core library shipped with the app or downloaded as a pack |
| `cached_record` (id, kind, json, fetched_at, etag) | Pages the user opened, readable offline |
| `saved_item` | User's saved medicines, papers, trials |
| `recent_search` | Search history, stored only on the device |

**Offline pack:** starts from the WHO Model List of Essential Medicines and the diseases,
herbs and institutions in the current built-in library. It downloads as a versioned
SQLite file, so updating the content doesn't need a new APK.

---

## 6. API (server)

| Endpoint | Returns |
|---|---|
| `GET /v1/search?q=&kinds=&cursor=` | Mixed results with kind, name, snippet |
| `GET /v1/entities/{id}` | Any page: compound, medicine, disease, trial… |
| `GET /v1/compounds/by-inchikey/{key}` | Exact compound |
| `POST /v1/compounds/similar` `{smiles, threshold}` | Similar structures |
| `POST /v1/compounds/substructure` `{smiles}` | Structures containing a fragment |
| `GET /v1/ingredients/{id}/products?country=IN` | Products in a country, with makers |
| `GET /v1/ingredients/{id}/interactions` | Interactions with severity and source |
| `GET /v1/entities/{id}/evidence?maxLevel=&predicate=` | Graded claims |
| `GET /v1/evidence/{claimId}` | The "Why do we believe this?" record |
| `GET /v1/trials?intervention=&condition=&status=` | Trials with results flags |
| `GET /v1/scan/code/{code}` | Product from a barcode or NDC |
| `GET /v1/offline-pack/latest` | Download link and version of the offline pack |

Every response includes the `sources` behind it.

---

## 7. Build order

1. **Now — app only (free):** built-in library + live public APIs. *(v0.5)*
2. **Pilot server:** load medicines first (openFDA, DailyMed, RxNorm, GSRS, CDSCO lists)
   plus the compounds of those ingredients — about tens of thousands of compounds.
   Add evidence claims from labels and trials.
3. **Research layer:** publications, trials (ClinicalTrials.gov, DRKS, CTRI), targets
   and bioactivity (ChEMBL), diseases (Orphanet, MeSH, ICD).
4. **Full chemistry:** bulk-load PubChem-scale compounds and RDKit structure search —
   only once RENK needs queries that PubChem's own service can't answer.
5. **Offline packs and scanning** tied to the `package` barcode table.

Each step works on its own. Step 4 is the only one that needs large storage.
