ALTER TABLE transaction ADD COLUMN transfer_account_id BIGINT NULL REFERENCES account (id);
ALTER TABLE transaction ADD COLUMN linked_transaction_id BIGINT NULL REFERENCES transaction (id);
