-- Every category now shares its root ancestor's color (V19 gave each row its own, independent
-- of its parent). Walk each category up to its root and copy that root's color down.
WITH RECURSIVE ancestry AS (
    SELECT id, color AS root_color
    FROM category
    WHERE parent_id IS NULL

    UNION ALL

    SELECT c.id, a.root_color
    FROM category c
    JOIN ancestry a ON c.parent_id = a.id
)
UPDATE category c
SET color = a.root_color
FROM ancestry a
WHERE c.id = a.id;
