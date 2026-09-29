-- Optional: structure search with the RDKit PostgreSQL cartridge.
-- Requires the rdkit extension installed on the server.
-- Enables: exact structure match, substructure search, and similarity
-- ("find molecules like this one") across the full compound table.

CREATE EXTENSION IF NOT EXISTS rdkit;
SET search_path = menk, public;

CREATE TABLE compound_mol (
  compound_id  bigint NOT NULL,
  m            mol,             -- RDKit molecule
  mfp2         bfp,             -- Morgan fingerprint radius 2, for similarity
  PRIMARY KEY (compound_id)
) PARTITION BY HASH (compound_id);
SELECT menk.make_hash_partitions('compound_mol', 64);

-- Fill after compounds are loaded (run per partition in parallel for 100M rows):
-- INSERT INTO compound_mol
--   SELECT id, mol_from_smiles(smiles::cstring), morganbv_fp(mol_from_smiles(smiles::cstring))
--   FROM compound WHERE mol_from_smiles(smiles::cstring) IS NOT NULL;

CREATE INDEX ON compound_mol USING gist (m);      -- substructure: WHERE m @> 'c1ccccc1C(=O)O'
CREATE INDEX ON compound_mol USING gist (mfp2);   -- similarity:   WHERE mfp2 % morganbv_fp('...')
