export interface Category {
  id: number;
  name: string;
  parentId: number | null;
  /** Pre-fills a transaction's target account when this category is picked and TRANSFERT is chosen. */
  defaultTransferAccountId: number | null;
  defaultTransferAccountName: string | null;
}

export interface CategoryRequest {
  name: string;
  parentId: number | null;
  defaultTransferAccountId: number | null;
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
