export interface Category {
  id: number;
  name: string;
  color: string;
  parentId: number | null;
  /** Pre-fills a transaction's target account when this category is picked and TRANSFERT is chosen. */
  defaultTransferAccountId: number | null;
  defaultTransferAccountName: string | null;
}

export interface CategoryRequest {
  name: string;
  parentId: number | null;
  defaultTransferAccountId: number | null;
  /** Omit (null) to let the server auto-assign a diverse color. */
  color: string | null;
}

const FALLBACK_CATEGORY_COLOR = '#94a3b8';

/** Looks up a category's badge color by id, falling back to a neutral gray for an
 *  uncategorized (null) or unknown id rather than leaving a table cell colorless. */
export function categoryColor(categories: Category[], categoryId: number | null): string {
  if (categoryId === null) {
    return FALLBACK_CATEGORY_COLOR;
  }
  return categories.find((c) => c.id === categoryId)?.color ?? FALLBACK_CATEGORY_COLOR;
}

export interface CategoryTreeNode extends Category {
  depth: number;
  children: CategoryTreeNode[];
}

export function buildCategoryTree(categories: Category[]): CategoryTreeNode[] {
  const nodes = new Map<number, CategoryTreeNode>(
    categories.map((category) => [category.id, { ...category, depth: 0, children: [] }]),
  );
  const roots: CategoryTreeNode[] = [];

  for (const node of nodes.values()) {
    if (node.parentId !== null && nodes.has(node.parentId)) {
      nodes.get(node.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }

  const flatten = (list: CategoryTreeNode[], depth: number): CategoryTreeNode[] =>
    list.flatMap((node) => {
      node.depth = depth;
      return [node, ...flatten(node.children, depth + 1)];
    });

  return flatten(roots, 0);
}

/** A category cannot become its own descendant's child: excludes itself and its whole subtree. */
export function excludeSubtree(categories: Category[], excludedId: number): Category[] {
  const descendantIds = new Set<number>([excludedId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const category of categories) {
      if (
        category.parentId !== null &&
        descendantIds.has(category.parentId) &&
        !descendantIds.has(category.id)
      ) {
        descendantIds.add(category.id);
        changed = true;
      }
    }
  }
  return categories.filter((category) => !descendantIds.has(category.id));
}
