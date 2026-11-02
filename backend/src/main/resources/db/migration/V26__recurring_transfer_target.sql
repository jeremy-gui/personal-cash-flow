-- A TRANSFERT recurring now knows its other account and direction, so its instances are real
-- transfers (negative on the default account, with a mirror row on the target account).
ALTER TABLE recurring_transaction ADD COLUMN transfer_account_id BIGINT REFERENCES account (id);
ALTER TABLE recurring_transaction ADD COLUMN outgoing BOOLEAN NOT NULL DEFAULT true;

UPDATE recurring_transaction r
SET transfer_account_id = c.default_transfer_account_id
FROM category c
WHERE c.id = r.category_id AND r.type = 'TRANSFERT';

-- Instances generated before that were broken transfers (positive amount, no target account).
DELETE FROM transaction
WHERE recurring_transaction_id IS NOT NULL
  AND type = 'TRANSFERT'
  AND transfer_account_id IS NULL
  AND status = 'PREVISIONNELLE';

-- Hand-made transfer prévisionnelles that already stand for a recurring's month (same label,
-- target and 1st-of-month date on the default account) become that month's instance, so the
-- generator doesn't add a second one next to them.
UPDATE transaction t
SET recurring_transaction_id = m.recurring_id
FROM (
    SELECT DISTINCT ON (r.id, t2.date) r.id AS recurring_id, t2.id AS transaction_id
    FROM recurring_transaction r
    JOIN transaction t2
      ON lower(t2.label) = lower(r.label)
     AND t2.transfer_account_id = r.transfer_account_id
     AND t2.date >= r.start_month
     AND EXTRACT(DAY FROM t2.date) = 1
    JOIN account a ON a.id = t2.account_id AND a.is_default
    WHERE r.type = 'TRANSFERT'
      AND t2.type = 'TRANSFERT'
      AND t2.status = 'PREVISIONNELLE'
      AND t2.source <> 'TRANSFER'
      AND t2.recurring_transaction_id IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM transaction x WHERE x.recurring_transaction_id = r.id AND x.date = t2.date)
    ORDER BY r.id, t2.date, t2.id
) m
WHERE t.id = m.transaction_id;
