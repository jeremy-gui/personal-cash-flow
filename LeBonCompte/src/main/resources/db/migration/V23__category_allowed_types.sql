-- A root category now lists the types its transactions may take (e.g. "REVENU,TRANSFERT"), instead
-- of a single default type; NULL = any type. Sub-categories inherit their root's list.
ALTER TABLE category ADD COLUMN allowed_types VARCHAR(60);
UPDATE category SET allowed_types = default_type WHERE default_type IS NOT NULL;
ALTER TABLE category DROP COLUMN default_type;
