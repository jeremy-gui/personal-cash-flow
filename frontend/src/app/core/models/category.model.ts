import { TransactionType } from './transaction.model';

export interface Category {
  id: number;
  name: string;
  color: string;
  parentId: number | null;
  /** Pre-fills a transaction's target account when this category is picked and TRANSFERT is chosen. */
  defaultTransferAccountId: number | null;
  defaultTransferAccountName: string | null;
  /** Types a hand-entered transaction may take - set on roots only, inherited (see allowedTypesFor); [] = any. */
  allowedTypes: TransactionType[];
}

export interface CategoryRequest {
  name: string;
  parentId: number | null;
  defaultTransferAccountId: number | null;
  /** Omit (null) to let the server auto-assign a diverse color. */
  color: string | null;
  /** [] = any type; ignored by the server on a sub-category. */
  allowedTypes: TransactionType[];
}

/** Every type, in the order forms list them - the first allowed one is the pre-selected default. */
export const ALL_TRANSACTION_TYPES: TransactionType[] = ['DEPENSE', 'REVENU', 'TRANSFERT'];

/** The types allowed in a category: its root's list (sub-categories inherit it), or every type. */
export function allowedTypesFor(categories: Category[], categoryId: number | null): TransactionType[] {
  const byId = new Map(categories.map((c) => [c.id, c]));
  let root = categoryId !== null ? byId.get(categoryId) : undefined;
  while (root?.parentId != null && byId.has(root.parentId)) {
    root = byId.get(root.parentId);
  }
  return root?.allowedTypes.length ? ALL_TRANSACTION_TYPES.filter((t) => root!.allowedTypes.includes(t)) : ALL_TRANSACTION_TYPES;
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
  /** Name indented with non-breaking spaces and a "└" branch, since native <option>s collapse regular
   *  whitespace - every category <select> uses this so the hierarchy stays visible. */
  optionLabel: string;
}

export function buildCategoryTree(categories: Category[]): CategoryTreeNode[] {
  const nodes = new Map<number, CategoryTreeNode>(
    categories.map((category) => [category.id, { ...category, depth: 0, children: [], optionLabel: category.name }]),
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
      node.optionLabel = depth === 0 ? node.name : '    '.repeat(depth - 1) + '  └ ' + node.name;
      return [node, ...flatten(node.children, depth + 1)];
    });

  roots.sort((a, b) => a.name.localeCompare(b.name, 'fr', { sensitivity: 'base' }));
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
