ALTER TABLE category ADD COLUMN color VARCHAR(7);

-- Backfill with a golden-angle hue rotation (same formula as CategoryColors.next in Java) so
-- existing categories get visually diverse colors, not all the same default.
WITH palette(idx, color) AS (
    VALUES
        (0, '#d74242'), (1, '#42d76d'), (2, '#9942d7'), (3, '#d7c442'), (4, '#42bed7'),
        (5, '#d74292'), (6, '#67d742'), (7, '#4842d7'), (8, '#d77442'), (9, '#42d79f'),
        (10, '#cb42d7'), (11, '#b8d742'), (12, '#428cd7'), (13, '#d74260'), (14, '#42d74e'),
        (15, '#7a42d7'), (16, '#d7a542'), (17, '#42d7d1'), (18, '#d742b1'), (19, '#86d742'),
        (20, '#425ad7'), (21, '#d75542'), (22, '#42d780'), (23, '#ac42d7'), (24, '#d6d742'),
        (25, '#42abd7'), (26, '#d7427f'), (27, '#54d742'), (28, '#5b42d7'), (29, '#d78742'),
        (30, '#42d7b2'), (31, '#d742d0'), (32, '#a4d742'), (33, '#4279d7'), (34, '#d7424d'),
        (35, '#42d761'), (36, '#8d42d7'), (37, '#d7b842'), (38, '#42cad7'), (39, '#d7429e')
),
numbered AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn FROM category
)
UPDATE category c
SET color = p.color
FROM numbered n
JOIN palette p ON p.idx = n.rn % 40
WHERE c.id = n.id;

ALTER TABLE category ALTER COLUMN color SET NOT NULL;
