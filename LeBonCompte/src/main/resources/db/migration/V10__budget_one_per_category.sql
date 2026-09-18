-- A budget now applies to a category from its start month onward, not just one specific month.
ALTER TABLE budget DROP CONSTRAINT budget_category_id_month_key;
ALTER TABLE budget RENAME COLUMN month TO start_month;
ALTER TABLE budget ADD CONSTRAINT uq_budget_category UNIQUE (category_id);
