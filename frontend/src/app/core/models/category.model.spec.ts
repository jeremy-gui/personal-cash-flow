import { describe, expect, it } from 'vitest';
import { Category, allowedTypesFor } from './category.model';

const category = (id: number, parentId: number | null, allowedTypes: Category['allowedTypes']): Category => ({
  id,
  name: `c${id}`,
  color: '#000000',
  parentId,
  defaultTransferAccountId: null,
  defaultTransferAccountName: null,
  allowedTypes,
});

describe('allowedTypesFor', () => {
  const categories = [
    category(1, null, ['TRANSFERT', 'REVENU']),
    category(2, 1, []),
    category(3, 2, []),
    category(4, null, []),
  ];

  it('gives a sub-category (at any depth) its root list, in form order', () => {
    expect(allowedTypesFor(categories, 3)).toEqual(['REVENU', 'TRANSFERT']);
  });

  it('allows every type without a category or when the root lists none', () => {
    expect(allowedTypesFor(categories, null)).toEqual(['DEPENSE', 'REVENU', 'TRANSFERT']);
    expect(allowedTypesFor(categories, 4)).toEqual(['DEPENSE', 'REVENU', 'TRANSFERT']);
  });
});
