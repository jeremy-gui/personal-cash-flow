-- Concurrent month generations used to insert the same recurring instance several times (a page
-- fires several requests at once). Keep the oldest copy of each (recurring, date), then forbid
-- duplicates for good. Recurring instances are always unmatched prévisionnelles with no transfer
-- link (matching deletes them), so removing the extra copies loses nothing.
DELETE FROM transaction t
USING transaction k
WHERE t.recurring_transaction_id = k.recurring_transaction_id
  AND t.date = k.date
  AND t.id > k.id;

CREATE UNIQUE INDEX uq_transaction_recurring_instance
    ON transaction (recurring_transaction_id, date)
    WHERE recurring_transaction_id IS NOT NULL;
