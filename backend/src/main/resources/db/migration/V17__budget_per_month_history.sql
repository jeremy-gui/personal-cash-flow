ALTER TABLE budget DROP CONSTRAINT uq_budget_category;
ALTER TABLE budget RENAME COLUMN start_month TO month;
ALTER TABLE budget ADD CONSTRAINT uq_budget_category_month UNIQUE (category_id, month);
