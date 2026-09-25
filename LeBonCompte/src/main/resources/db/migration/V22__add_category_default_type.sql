-- REVENU/DEPENSE pre-selected when a transaction is entered by hand in the category (NULL = none).
ALTER TABLE category ADD COLUMN default_type VARCHAR(20);
