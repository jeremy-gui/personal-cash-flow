ALTER TABLE category ADD COLUMN default_transfer_account_id BIGINT NULL REFERENCES account (id);
