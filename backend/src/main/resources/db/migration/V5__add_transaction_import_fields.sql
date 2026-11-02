ALTER TABLE transaction ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE transaction ADD COLUMN import_hash VARCHAR(300);
ALTER TABLE transaction ADD COLUMN raw_import_data TEXT;

ALTER TABLE transaction
    ADD CONSTRAINT uq_transaction_account_import_hash UNIQUE (account_id, import_hash);
