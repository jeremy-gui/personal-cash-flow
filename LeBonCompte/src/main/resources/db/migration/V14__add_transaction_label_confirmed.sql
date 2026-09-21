-- False only while the label was auto-suggested from import history and not yet reviewed by the
-- user; true for every label set manually, by reconciliation, or already existing before this
-- column was added.
ALTER TABLE transaction ADD COLUMN label_confirmed BOOLEAN NOT NULL DEFAULT true;
