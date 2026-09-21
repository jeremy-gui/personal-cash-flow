-- False only while a category was auto-suggested from import history and not yet reviewed by the
-- user; true for every category set manually, by reconciliation, or already existing before this
-- column was added.
ALTER TABLE transaction ADD COLUMN category_confirmed BOOLEAN NOT NULL DEFAULT true;
