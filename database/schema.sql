-- =====================================================================
-- MENK knowledge database — PostgreSQL 15+ schema
-- Scale target: 100M+ chemical compounds, millions of medicine products,
-- tens of millions of publications, trials and bioactivity records.
--
-- Principles
--   1. Every node (compound, medicine, disease, trial, paper...) gets one
--      global id from the ENTITY table, so relationships and evidence can
--      point at anything with real foreign keys.
--   2. Every fact carries a source_id (provenance + license).
--   3. The biggest tables are hash-partitioned so they load, index and
--      vacuum in manageable pieces.
--   4. Evidence claims are graded L1–L8 and never collapsed into one score.
--
-- Load order: this file, then (optional) chem_search_rdkit.sql.
-- During bulk loads, create partitions first, COPY data in, then build
-- secondary indexes and validate foreign keys.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS menk;
SET search_path = menk, public;

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- fuzzy name matching

-- Helper: create N hash partitions for a partitioned table.
CREATE OR REPLACE FUNCTION menk.make_hash_partitions(parent text, n int)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  FOR i IN 0..n-1 LOOP
    EXECUTE format(
      'CREATE TABLE IF NOT EXISTS menk.%I PARTITION OF menk.%I FOR VALUES WITH (MODULUS %s, REMAINDER %s)',
      parent || '_p' || lpad(i::text, 2, '0'), parent, n, i);
  END LOOP;
END $$;

-- =====================================================================
-- 1. PROVENANCE: where every fact comes from, and under what license
-- =====================================================================

CREATE TABLE source (
  id               smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  code             text NOT NULL UNIQUE,        -- 'PUBCHEM', 'OPENFDA_LABEL', 'CTGOV', 'DRKS', 'CHEMBL'
  name             text NOT NULL,
  organization     text,
  homepage_url     text,
  tier             smallint NOT NULL CHECK (tier BETWEEN 1 AND 4),
                   -- 1 regulator/official, 2 peer-reviewed/scientific DB, 3 industry filings, 4 company/other
  license          text NOT NULL,               -- e.g. 'Public domain (US Gov)', 'CC BY-SA 3.0'
  license_url      text,
  commercial_use   boolean,
  redistribution   boolean,
  attribution_text text
);

CREATE TABLE source_release (
  id            integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source_id     smallint NOT NULL REFERENCES source(id),
  version       text NOT NULL,                  -- e.g. 'ChEMBL 35', '2026-09-01 dump'
  released_on   date,
  loaded_at     timestamptz,
  record_count  bigint,
  UNIQUE (source_id, version)
);

CREATE TABLE ingest_run (
  id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source_release_id  integer NOT NULL REFERENCES source_release(id),
  started_at         timestamptz NOT NULL DEFAULT now(),
  finished_at        timestamptz,
  status             text NOT NULL DEFAULT 'running' CHECK (status IN ('running','ok','failed')),
  rows_in            bigint DEFAULT 0,
  rows_upserted      bigint DEFAULT 0,
  rows_rejected      bigint DEFAULT 0,
  log                text
);

-- =====================================================================
-- 2. ENTITY BACKBONE: one global id per thing
-- =====================================================================

CREATE TABLE entity_kind (
  id    smallint PRIMARY KEY,
  code  text NOT NULL UNIQUE
);
INSERT INTO entity_kind VALUES
  (1,'compound'),(2,'substance'),(3,'ingredient'),(4,'product'),(5,'disease'),
  (6,'target'),(7,'pathway'),(8,'organization'),(9,'trial'),(10,'publication'),
  (11,'reaction'),(12,'organism'),(13,'assay');

CREATE TABLE entity (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  kind        smallint NOT NULL REFERENCES entity_kind(id),
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- External identifiers for ANY entity (CAS, CID, ChEMBL, UNII, RxCUI, ATC,
-- NDC, ICD-10/11, MeSH, UniProt, ROR, LEI, NCT, DRKS, CTRI, PMID, DOI...)
CREATE TABLE id_namespace (
  id     smallint PRIMARY KEY,
  code   text NOT NULL UNIQUE,
  label  text NOT NULL
);
INSERT INTO id_namespace VALUES
  (1,'PUBCHEM_CID','PubChem compound'),(2,'PUBCHEM_SID','PubChem substance'),
  (3,'CAS','CAS Registry Number'),(4,'CHEBI','ChEBI'),(5,'CHEMBL','ChEMBL'),
  (6,'UNII','FDA UNII'),(7,'RXCUI','RxNorm'),(8,'ATC','WHO ATC'),(9,'INN','WHO INN'),
  (10,'NDC','US NDC'),(11,'GTIN','GS1 GTIN'),(12,'ICD10','ICD-10'),(13,'ICD11','ICD-11'),
  (14,'MESH','MeSH'),(15,'MONDO','MONDO'),(16,'ORPHA','Orphanet'),(17,'UNIPROT','UniProt'),
  (18,'ENSEMBL','Ensembl'),(19,'ROR','ROR'),(20,'LEI','GLEIF LEI'),(21,'NCT','ClinicalTrials.gov'),
  (22,'DRKS','German Clinical Trials Register'),(23,'CTRI','Clinical Trials Registry India'),
  (24,'EUCT','EU CTIS'),(25,'PMID','PubMed'),(26,'PMCID','PubMed Central'),(27,'DOI','DOI'),
  (28,'RHEA','Rhea reaction'),(29,'IMPPAT','IMPPAT'),(30,'REACTOME','Reactome'),
  (31,'NCBI_TAXON','NCBI Taxonomy'),(32,'FDA_APPL','FDA application number');

CREATE TABLE identifier (
  namespace_id  smallint NOT NULL REFERENCES id_namespace(id),
  value         text NOT NULL,
  entity_id     bigint NOT NULL REFERENCES entity(id),
  source_id     smallint REFERENCES source(id),
  PRIMARY KEY (namespace_id, value, entity_id)
) PARTITION BY HASH (value);
SELECT menk.make_hash_partitions('identifier', 32);
CREATE INDEX ON identifier (entity_id);

-- Tracks each upstream record, for incremental updates and change detection.
CREATE TABLE source_record (
  source_id     smallint NOT NULL REFERENCES source(id),
  external_id   text NOT NULL,
  entity_id     bigint REFERENCES entity(id),
  content_hash  bytea,                 -- skip unchanged records on re-ingest
  fetched_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (source_id, external_id)
) PARTITION BY HASH (external_id);
SELECT menk.make_hash_partitions('source_record', 32);

-- =====================================================================
-- 3. CHEMISTRY (100M+ rows)
-- =====================================================================

CREATE TABLE compound (
  id            bigint NOT NULL REFERENCES entity(id),
  inchikey      char(27) NOT NULL,
  inchi         text,
  smiles        text NOT NULL,          -- canonical isomeric SMILES
  formula       text,
  mol_weight    numeric(12,4),
  exact_mass    numeric(14,6),
  charge        smallint,
  heavy_atoms   smallint,
  xlogp         real,
  tpsa          real,
  hbd           smallint,
  hba           smallint,
  rot_bonds     smallint,
  complexity    real,
  parent_id     bigint,                 -- salt-stripped / neutral parent compound
  pubchem_cid   bigint,
  source_id     smallint REFERENCES source(id),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id)
) PARTITION BY HASH (id);
SELECT menk.make_hash_partitions('compound', 64);
CREATE INDEX ON compound (pubchem_cid);
CREATE INDEX ON compound (formula);
CREATE INDEX ON compound (parent_id);

-- Global uniqueness of InChIKey (a unique index on a hash-partitioned table
-- must include the partition key, so the dedup lookup lives in its own table).
CREATE TABLE compound_inchikey (
  inchikey     char(27) PRIMARY KEY,
  compound_id  bigint NOT NULL
) PARTITION BY HASH (inchikey);
SELECT menk.make_hash_partitions('compound_inchikey', 32);

CREATE TABLE compound_name (
  compound_id  bigint NOT NULL,
  name         text NOT NULL,
  name_type    smallint NOT NULL,       -- 1 preferred, 2 IUPAC, 3 synonym, 4 INN, 5 brand
  lang         char(2) DEFAULT 'en',
  source_id    smallint REFERENCES source(id)
) PARTITION BY HASH (compound_id);
SELECT menk.make_hash_partitions('compound_name', 64);
CREATE INDEX ON compound_name (compound_id);
CREATE INDEX ON compound_name (lower(name));

-- Large structure blobs kept apart so the main table stays small and fast.
CREATE TABLE compound_structure (
  compound_id   bigint NOT NULL,
  molblock_2d   bytea,                  -- compressed MDL molfile
  molblock_3d   bytea,                  -- compressed 3D conformer
  PRIMARY KEY (compound_id)
) PARTITION BY HASH (compound_id);
SELECT menk.make_hash_partitions('compound_structure', 64);

CREATE TABLE property_def (
  id     smallint PRIMARY KEY,
  code   text NOT NULL UNIQUE,          -- 'MELTING_POINT', 'WATER_SOLUBILITY', 'PKA'
  name   text NOT NULL,
  unit   text
);

-- Experimental and extra computed properties (sparse, so not columns).
CREATE TABLE compound_property (
  compound_id   bigint NOT NULL,
  property_id   smallint NOT NULL REFERENCES property_def(id),
  value_num     double precision,
  value_text    text,
  method        text CHECK (method IN ('experimental','computed')),
  conditions    text,
  source_id     smallint REFERENCES source(id),
  publication_id bigint
) PARTITION BY HASH (compound_id);
SELECT menk.make_hash_partitions('compound_property', 32);
CREATE INDEX ON compound_property (compound_id, property_id);

CREATE TABLE compound_hazard (
  compound_id  bigint NOT NULL,
  ghs_code     text NOT NULL,           -- e.g. 'H225'
  statement    text,
  pictogram    text,
  source_id    smallint REFERENCES source(id)
);
CREATE INDEX ON compound_hazard (compound_id);

CREATE TABLE toxicity (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  compound_id   bigint NOT NULL,
  endpoint      text NOT NULL,          -- 'LD50', 'NOAEL', 'carcinogenicity'
  species       text,
  route         text,
  value_num     double precision,
  unit          text,
  source_id     smallint REFERENCES source(id),
  publication_id bigint
);
CREATE INDEX ON toxicity (compound_id);

-- Substances: things that may not have one defined structure
-- (proteins, antibodies, mixtures, polymers, herbal extracts, cell/gene therapies).
CREATE TABLE substance (
  id               bigint PRIMARY KEY REFERENCES entity(id),
  name             text NOT NULL,
  substance_class  text NOT NULL CHECK (substance_class IN
                     ('chemical','protein','nucleic_acid','mixture','polymer',
                      'structurally_diverse','cell_therapy','gene_therapy')),
  unii             char(10),
  compound_id      bigint,              -- set when it is one defined molecule
  description      text,
  source_id        smallint REFERENCES source(id)
);
CREATE INDEX ON substance (compound_id);
CREATE INDEX ON substance USING gin (name gin_trgm_ops);

CREATE TABLE substance_component (
  substance_id    bigint NOT NULL REFERENCES substance(id),
  component_id    bigint NOT NULL REFERENCES entity(id),   -- a compound or another substance
  role            text,                  -- 'active', 'constituent', 'marker'
  amount_text     text,
  PRIMARY KEY (substance_id, component_id)
);

-- =====================================================================
-- 4. ORGANIZATIONS AND COUNTRIES
-- =====================================================================

CREATE TABLE country (
  code  char(2) PRIMARY KEY,             -- ISO 3166-1 alpha-2
  name  text NOT NULL
);

CREATE TABLE organization (
  id          bigint PRIMARY KEY REFERENCES entity(id),
  name        text NOT NULL,
  org_type    text NOT NULL CHECK (org_type IN
                ('manufacturer','sponsor','regulator','university','research_institute',
                 'hospital','registry','network','distributor','other')),
  country     char(2) REFERENCES country(code),
  city        text,
  parent_id   bigint REFERENCES organization(id),
  lei         char(20),
  ror         text,
  website     text,
  source_id   smallint REFERENCES source(id)
);
CREATE INDEX ON organization USING gin (name gin_trgm_ops);
CREATE INDEX ON organization (parent_id);

-- =====================================================================
-- 5. MEDICINES (millions of products across countries)
-- =====================================================================

-- Active ingredient = what does the work; links to a substance.
CREATE TABLE ingredient (
  id               bigint PRIMARY KEY REFERENCES entity(id),
  substance_id     bigint NOT NULL REFERENCES substance(id),
  inn_name         text,                 -- WHO International Nonproprietary Name
  moiety_id        bigint REFERENCES ingredient(id),  -- salt/ester -> active moiety
  is_biologic      boolean NOT NULL DEFAULT false
);
CREATE INDEX ON ingredient (substance_id);

CREATE TABLE drug_class (
  id         integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  system     text NOT NULL,              -- 'ATC', 'EPC', 'MOA'
  code       text NOT NULL,
  name       text NOT NULL,
  parent_id  integer REFERENCES drug_class(id),
  UNIQUE (system, code)
);

CREATE TABLE ingredient_class (
  ingredient_id  bigint NOT NULL REFERENCES ingredient(id),
  class_id       integer NOT NULL REFERENCES drug_class(id),
  PRIMARY KEY (ingredient_id, class_id)
);

-- A product = one brand/formulation in one country.
CREATE TABLE product (
  id                  bigint PRIMARY KEY REFERENCES entity(id),
  name                text NOT NULL,
  country             char(2) NOT NULL REFERENCES country(code),
  dosage_form         text,
  route               text,
  rx_status           text CHECK (rx_status IN ('rx','otc','hospital','other')),
  marketing_category  text,              -- 'NDA', 'ANDA', 'BLA', 'OTC monograph', 'unapproved'...
  holder_org_id       bigint REFERENCES organization(id),
  status              text CHECK (status IN ('marketed','discontinued','suspended','withdrawn')),
  source_id           smallint REFERENCES source(id),
  updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON product (country, status);
CREATE INDEX ON product (holder_org_id);
CREATE INDEX ON product USING gin (name gin_trgm_ops);

CREATE TABLE product_ingredient (
  product_id     bigint NOT NULL REFERENCES product(id),
  ingredient_id  bigint NOT NULL REFERENCES ingredient(id),
  strength_num   numeric,
  strength_unit  text,
  per_num        numeric,
  per_unit       text,
  PRIMARY KEY (product_id, ingredient_id)
);
CREATE INDEX ON product_ingredient (ingredient_id);

CREATE TABLE package (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id      bigint NOT NULL REFERENCES product(id),
  code            text NOT NULL,          -- NDC package code, GTIN barcode...
  code_type       text NOT NULL,          -- 'NDC', 'GTIN', 'national'
  description     text,
  labeler_org_id  bigint REFERENCES organization(id),
  UNIQUE (code_type, code)
);

-- Manufacturing: which sites make which products.
CREATE TABLE manufacturing_site (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  org_id      bigint NOT NULL REFERENCES organization(id),
  country     char(2) REFERENCES country(code),
  city        text,
  fei_number  text,                        -- FDA Establishment Identifier
  site_type   text                         -- 'API', 'finished dose', 'packaging', 'testing'
);

CREATE TABLE product_site (
  product_id  bigint NOT NULL REFERENCES product(id),
  site_id     bigint NOT NULL REFERENCES manufacturing_site(id),
  role        text,
  PRIMARY KEY (product_id, site_id)
);

-- Regulatory decisions, per agency and country.
CREATE TABLE marketing_authorization (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id        bigint REFERENCES product(id),
  ingredient_id     bigint REFERENCES ingredient(id),
  regulator_org_id  bigint NOT NULL REFERENCES organization(id),
  country           char(2) REFERENCES country(code),
  number            text,                   -- 'NDA020357', EU/3/..., CDSCO approval no.
  auth_type         text,                   -- 'new drug', 'generic', 'biologic', 'EUA', 'orphan'
  status            text NOT NULL CHECK (status IN
                      ('approved','conditional','emergency','withdrawn','suspended','refused','pending')),
  decided_on        date,
  ended_on          date,
  source_id         smallint REFERENCES source(id),
  source_url        text,
  CHECK (product_id IS NOT NULL OR ingredient_id IS NOT NULL)
);
CREATE INDEX ON marketing_authorization (product_id);
CREATE INDEX ON marketing_authorization (ingredient_id);
CREATE INDEX ON marketing_authorization (regulator_org_id, status);

CREATE TABLE recall (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id        bigint REFERENCES product(id),
  package_id        bigint REFERENCES package(id),
  firm_org_id       bigint REFERENCES organization(id),
  regulator_org_id  bigint REFERENCES organization(id),
  classification    text,                   -- 'Class I', 'Class II', 'Class III'
  reason            text,
  reported_on       date,
  status            text,
  source_id         smallint REFERENCES source(id)
);
CREATE INDEX ON recall (product_id);

-- Official labels (FDA SPL, EU SmPC, CDSCO package inserts).
CREATE TABLE label_document (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  product_id        bigint REFERENCES product(id),
  regulator_org_id  bigint REFERENCES organization(id),
  set_id            text,
  version           integer,
  effective_on      date,
  url               text,
  source_id         smallint REFERENCES source(id)
);
CREATE INDEX ON label_document (product_id);

CREATE TABLE label_section (
  label_id      bigint NOT NULL REFERENCES label_document(id),
  section_code  text NOT NULL,             -- LOINC section code for FDA SPL
  title         text,
  body          text,
  PRIMARY KEY (label_id, section_code)
);

-- =====================================================================
-- 6. BIOLOGY: diseases, targets, pathways, bioactivity
-- =====================================================================

CREATE TABLE disease (
  id           bigint PRIMARY KEY REFERENCES entity(id),
  name         text NOT NULL,
  description  text,
  parent_id    bigint REFERENCES disease(id),
  is_rare      boolean DEFAULT false
);
CREATE INDEX ON disease USING gin (name gin_trgm_ops);

CREATE TABLE target (
  id            bigint PRIMARY KEY REFERENCES entity(id),
  name          text NOT NULL,
  target_type   text,                     -- 'protein', 'complex', 'gene', 'RNA'
  uniprot       text,
  gene_symbol   text,
  taxon_id      integer                   -- 9606 = human
);
CREATE INDEX ON target (gene_symbol);

CREATE TABLE pathway (
  id         bigint PRIMARY KEY REFERENCES entity(id),
  name       text NOT NULL,
  source_id  smallint REFERENCES source(id)
);

CREATE TABLE target_pathway (
  target_id   bigint NOT NULL REFERENCES target(id),
  pathway_id  bigint NOT NULL REFERENCES pathway(id),
  PRIMARY KEY (target_id, pathway_id)
);

CREATE TABLE assay (
  id              bigint PRIMARY KEY REFERENCES entity(id),
  description     text,
  assay_type      text,                   -- 'binding', 'functional', 'ADMET', 'toxicity'
  organism        text,
  source_id       smallint REFERENCES source(id),
  publication_id  bigint
);

-- Tens of millions of measurements (ChEMBL, PubChem BioAssay).
CREATE SEQUENCE bioactivity_id_seq;
CREATE TABLE bioactivity (
  id             bigint NOT NULL DEFAULT nextval('menk.bioactivity_id_seq'),
  compound_id    bigint NOT NULL,
  target_id      bigint,
  assay_id       bigint,
  activity_type  text,                    -- 'IC50', 'Ki', 'EC50'
  relation       text,                    -- '=', '<', '>'
  value_num      double precision,
  unit           text,
  pchembl        real,                    -- normalised potency (-log molar)
  source_id      smallint REFERENCES source(id),
  PRIMARY KEY (compound_id, id)
) PARTITION BY HASH (compound_id);
SELECT menk.make_hash_partitions('bioactivity', 32);
CREATE INDEX ON bioactivity (target_id);

-- How a medicine acts on its target.
CREATE TABLE mechanism (
  ingredient_id  bigint NOT NULL REFERENCES ingredient(id),
  target_id      bigint NOT NULL REFERENCES target(id),
  action_type    text NOT NULL,           -- 'inhibitor', 'agonist', 'antagonist', 'blocker'
  description    text,
  source_id      smallint REFERENCES source(id),
  PRIMARY KEY (ingredient_id, target_id, action_type)
);

-- =====================================================================
-- 7. PLANTS, NATURAL PRODUCTS, TRADITIONAL MEDICINE
-- =====================================================================

CREATE TABLE organism (
  id               bigint PRIMARY KEY REFERENCES entity(id),
  scientific_name  text NOT NULL,
  family           text,
  taxon_id         integer,
  common_names     text[]
);
CREATE INDEX ON organism USING gin (scientific_name gin_trgm_ops);

CREATE TABLE natural_occurrence (
  organism_id     bigint NOT NULL REFERENCES organism(id),
  compound_id     bigint NOT NULL,
  plant_part      text,                   -- 'rhizome', 'leaf', 'seed'
  source_id       smallint REFERENCES source(id),
  publication_id  bigint,
  PRIMARY KEY (organism_id, compound_id, plant_part)
);
CREATE INDEX ON natural_occurrence (compound_id);

CREATE TABLE traditional_use (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  organism_id   bigint NOT NULL REFERENCES organism(id),
  system        text NOT NULL,            -- 'Ayurveda', 'Siddha', 'Unani', 'Sowa-Rigpa', 'TCM', 'folk'
  plant_part    text,
  use_text      text NOT NULL,
  source_id     smallint REFERENCES source(id)
);

-- =====================================================================
-- 8. REACTIONS (educational and biochemical scope)
-- =====================================================================

CREATE TABLE reaction (
  id              bigint PRIMARY KEY REFERENCES entity(id),
  reaction_smiles text,
  reaction_class  text,                   -- 'redox', 'hydrolysis', 'acid-base', enzymatic...
  is_biochemical  boolean NOT NULL DEFAULT false,
  ec_number       text,                   -- enzyme class for biochemical reactions
  balanced        boolean,
  source_id       smallint REFERENCES source(id),
  publication_id  bigint
);

CREATE TABLE reaction_participant (
  reaction_id  bigint NOT NULL REFERENCES reaction(id),
  compound_id  bigint NOT NULL,
  role         text NOT NULL CHECK (role IN ('reactant','product','catalyst','cofactor','solvent')),
  stoich       numeric,
  PRIMARY KEY (reaction_id, compound_id, role)
);
CREATE INDEX ON reaction_participant (compound_id);

-- =====================================================================
-- 9. RESEARCH: publications and clinical trials
-- =====================================================================

CREATE TABLE publication (
  id          bigint PRIMARY KEY REFERENCES entity(id),
  pmid        bigint UNIQUE,
  pmcid       text UNIQUE,
  doi         text UNIQUE,
  title       text NOT NULL,
  journal     text,
  pub_year    smallint,
  pub_types   text[],                     -- 'Randomized Controlled Trial', 'Meta-Analysis'...
  is_retracted boolean NOT NULL DEFAULT false
);
CREATE INDEX ON publication (pub_year);

-- Which entities each paper is about (hundreds of millions of rows).
CREATE TABLE entity_mention (
  entity_id       bigint NOT NULL,
  publication_id  bigint NOT NULL,
  mention_type    text,                   -- 'annotated', 'mesh', 'text-mined'
  source_id       smallint REFERENCES source(id),
  PRIMARY KEY (entity_id, publication_id)
) PARTITION BY HASH (entity_id);
SELECT menk.make_hash_partitions('entity_mention', 64);

CREATE TABLE clinical_trial (
  id                bigint PRIMARY KEY REFERENCES entity(id),
  registry          text NOT NULL,          -- 'CTGOV', 'DRKS', 'CTRI', 'EUCT', 'ICTRP'
  registry_id       text NOT NULL,
  title             text NOT NULL,
  phase             text,
  status            text,
  study_type        text,
  enrollment        integer,
  enrollment_type   text,                   -- 'actual', 'estimated'
  start_date        date,
  completion_date   date,
  sponsor_org_id    bigint REFERENCES organization(id),
  has_results       boolean NOT NULL DEFAULT false,
  why_stopped       text,
  last_updated      date,
  UNIQUE (registry, registry_id)
);
CREATE INDEX ON clinical_trial (status, phase);

CREATE TABLE trial_intervention (
  trial_id   bigint NOT NULL REFERENCES clinical_trial(id),
  entity_id  bigint NOT NULL REFERENCES entity(id),   -- ingredient, product or substance
  arm_label  text,
  PRIMARY KEY (trial_id, entity_id)
);
CREATE INDEX ON trial_intervention (entity_id);

CREATE TABLE trial_condition (
  trial_id        bigint NOT NULL REFERENCES clinical_trial(id),
  disease_id      bigint REFERENCES disease(id),
  condition_text  text NOT NULL
);
CREATE INDEX ON trial_condition (disease_id);

CREATE TABLE trial_site (
  trial_id  bigint NOT NULL REFERENCES clinical_trial(id),
  org_id    bigint REFERENCES organization(id),
  country   char(2) REFERENCES country(code)
);
CREATE INDEX ON trial_site (trial_id);

CREATE TABLE trial_outcome (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  trial_id     bigint NOT NULL REFERENCES clinical_trial(id),
  outcome_type text NOT NULL CHECK (outcome_type IN ('primary','secondary','other','adverse_events')),
  measure      text NOT NULL,
  time_frame   text,
  result       jsonb                        -- groups, values, units, analyses as posted
);
CREATE INDEX ON trial_outcome (trial_id);

-- =====================================================================
-- 10. CLINICAL FACTS ABOUT MEDICINES
-- =====================================================================

CREATE TABLE indication (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  ingredient_id     bigint NOT NULL REFERENCES ingredient(id),
  disease_id        bigint NOT NULL REFERENCES disease(id),
  status            text NOT NULL CHECK (status IN ('approved','investigational','off_label_reported')),
  regulator_org_id  bigint REFERENCES organization(id),
  country           char(2) REFERENCES country(code),
  source_id         smallint REFERENCES source(id)
);
CREATE INDEX ON indication (ingredient_id);
CREATE INDEX ON indication (disease_id);

-- Drug–drug, drug–food and drug–herb interactions.
CREATE TABLE interaction (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  a_id         bigint NOT NULL REFERENCES entity(id),
  b_id         bigint NOT NULL REFERENCES entity(id),
  severity     text CHECK (severity IN ('contraindicated','major','moderate','minor','unknown')),
  mechanism    text,                      -- 'pharmacokinetic (CYP3A4)', 'additive bleeding risk'
  effect       text NOT NULL,
  management   text,
  source_id    smallint NOT NULL REFERENCES source(id),
  CHECK (a_id < b_id),                    -- store each pair once
  UNIQUE (a_id, b_id, source_id)
);
CREATE INDEX ON interaction (b_id);

CREATE TABLE adverse_effect (
  ingredient_id  bigint NOT NULL REFERENCES ingredient(id),
  term           text NOT NULL,
  meddra_code    text,
  frequency      text,                    -- 'very common', 'rare'...
  serious        boolean,
  source_id      smallint REFERENCES source(id)
);
CREATE INDEX ON adverse_effect (ingredient_id);

-- =====================================================================
-- 11. EVIDENCE: the heart of MENK — every claim, graded and sourced
-- =====================================================================

CREATE TABLE evidence_level (
  level  smallint PRIMARY KEY CHECK (level BETWEEN 1 AND 8),
  name   text NOT NULL,
  human  boolean NOT NULL
);
INSERT INTO evidence_level VALUES
  (1,'Regulatory / official label',true),(2,'Systematic review / meta-analysis',true),
  (3,'Clinical trial',true),(4,'Observational human study',true),(5,'Animal study',false),
  (6,'Laboratory / biochemical',false),(7,'Computational prediction',false),
  (8,'Hypothesis / traditional use',false);

CREATE TABLE predicate (
  id    smallint PRIMARY KEY,
  code  text NOT NULL UNIQUE,
  name  text NOT NULL
);
INSERT INTO predicate VALUES
  (1,'APPROVED_FOR','is approved for'),(2,'STUDIED_FOR','is studied for'),
  (3,'TREATS','shows benefit in'),(4,'NO_BENEFIT','shows no benefit in'),
  (5,'INHIBITS','inhibits'),(6,'ACTIVATES','activates'),(7,'BINDS','binds'),
  (8,'CAUSES_ADVERSE','causes adverse effect'),(9,'INTERACTS_WITH','interacts with'),
  (10,'ASSOCIATED_WITH','is associated with'),(11,'CONTAINS','contains'),
  (12,'METABOLIZED_BY','is metabolized by'),(13,'TRADITIONALLY_USED_FOR','is traditionally used for');

CREATE SEQUENCE evidence_claim_id_seq;
CREATE TABLE evidence_claim (
  id              bigint NOT NULL DEFAULT nextval('menk.evidence_claim_id_seq'),
  subject_id      bigint NOT NULL,        -- entity: e.g. ingredient
  predicate_id    smallint NOT NULL REFERENCES predicate(id),
  object_id       bigint NOT NULL,        -- entity: e.g. disease or target
  level           smallint NOT NULL REFERENCES evidence_level(level),
  direction       smallint CHECK (direction IN (-1,0,1)),  -- harms / no effect / benefit
  species         text,
  population      text,
  sample_size     integer,
  summary         text,
  limitations     text,
  publication_id  bigint,
  trial_id        bigint,
  source_id       smallint NOT NULL REFERENCES source(id),
  source_record   text,                   -- upstream id for "Why do we believe this?"
  status          text NOT NULL DEFAULT 'active' CHECK (status IN ('active','superseded','retracted')),
  retrieved_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (subject_id, id)
) PARTITION BY HASH (subject_id);
SELECT menk.make_hash_partitions('evidence_claim', 32);
CREATE INDEX ON evidence_claim (object_id, predicate_id);
CREATE INDEX ON evidence_claim (level);

-- Foreign keys from partitioned children to ENTITY are added after bulk load:
--   ALTER TABLE evidence_claim ADD FOREIGN KEY (subject_id) REFERENCES entity(id) NOT VALID;
--   ALTER TABLE evidence_claim VALIDATE CONSTRAINT <name>;
-- (Same pattern for compound_name, compound_structure, compound_property,
--  bioactivity, entity_mention.)
