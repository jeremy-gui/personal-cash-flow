-- A recurring transaction whose amount changes month to month (EDF, courses...): its amount is an estimate.
ALTER TABLE recurring_transaction ADD COLUMN variable_amount BOOLEAN NOT NULL DEFAULT false;
