-- ZK-RFA (Zero-Knowledge Redactable Financial Audit) fields on event_log
ALTER TABLE event_log ADD COLUMN chameleon_randomness TEXT;
ALTER TABLE event_log ADD COLUMN mmr_index BIGINT;
ALTER TABLE event_log ADD COLUMN mmr_root VARCHAR(128);
ALTER TABLE event_log ADD COLUMN mmr_proof_json TEXT;

CREATE INDEX idx_event_log_mmr ON event_log(mmr_index);
