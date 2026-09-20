-- Marks a POINTEE row that already absorbed a previsionnelle via reconciliation, so it stops
-- being offered as a rapprochement candidate to other previsionnelles.
ALTER TABLE transaction ADD COLUMN matched BOOLEAN NOT NULL DEFAULT false;
