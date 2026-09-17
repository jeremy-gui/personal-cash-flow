ALTER TABLE account ADD COLUMN account_number VARCHAR(50);
ALTER TABLE account ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE account ADD CONSTRAINT uq_account_account_number UNIQUE (account_number);
CREATE UNIQUE INDEX uq_account_default ON account (is_default) WHERE is_default = true;

-- Preserve a working upgrade for the existing single-account install: without this,
-- every account starts with is_default = false and the Dashboard breaks immediately.
UPDATE account SET is_default = true WHERE id = (SELECT MIN(id) FROM account);
