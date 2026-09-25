import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Account } from '../../core/models/account.model';
import { Budget } from '../../core/models/budget.model';
import { buildCategoryTree, Category } from '../../core/models/category.model';
import {
  TRANSACTION_TYPE_LABELS,
  Transaction,
  TransactionRequest,
  TransactionType,
} from '../../core/models/transaction.model';
import { AccountService } from '../accounts/account.service';
import { BudgetService } from '../budgets/budget.service';
import { CategoryService } from '../categories/category.service';
import { MatchPickerComponent } from './match-picker.component';
import { TransactionService } from './transaction.service';

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

function normalizeTokens(label: string): Set<string> {
  return new Set(
    normalizeText(label)
      .split(/[^a-z0-9]+/)
      .filter((token) => token.length > 1),
  );
}

/** Fraction of `a`'s tokens found in `b` - simple, dependency-free, mirrors the backend's OFX label matching. */
function labelSimilarity(a: string, b: string): number {
  const tokensA = normalizeTokens(a);
  if (tokensA.size === 0) {
    return 0;
  }
  const tokensB = normalizeTokens(b);
  let overlap = 0;
  for (const token of tokensA) {
    if (tokensB.has(token)) {
      overlap++;
    }
  }
  return overlap / tokensA.size;
}

function sameAmount(a: number, b: number): boolean {
  return Math.round(a * 100) === Math.round(b * 100);
}

type RowSort = 'date-desc' | 'date-asc' | 'amount-desc';

/** Previsionnelles always lead (they are what's still to come), then the chosen order. */
function sortRows(transactions: Transaction[], sort: RowSort): Transaction[] {
  return [...transactions].sort((a, b) => {
    if (a.status !== b.status) {
      return a.status === 'PREVISIONNELLE' ? -1 : 1;
    }
    switch (sort) {
      case 'date-asc':
        return a.date.localeCompare(b.date);
      case 'amount-desc':
        return Math.abs(b.amount) - Math.abs(a.amount);
      default:
        return b.date.localeCompare(a.date);
    }
  });
}

interface Totals {
  net: number;
  pointee: number;
  previsionnelle: number;
  count: number;
}

function totalsOf(transactions: Transaction[]): Totals {
  let pointee = 0;
  let previsionnelle = 0;
  for (const t of transactions) {
    if (t.status === 'PREVISIONNELLE') {
      previsionnelle += t.amount;
    } else {
      pointee += t.amount;
    }
  }
  return { net: pointee + previsionnelle, pointee, previsionnelle, count: transactions.length };
}

export interface CardSection {
  key: string;
  title: string;
  totals: Totals;
  budget: number | null;
  transactions: Transaction[];
}

export interface CategoryCard {
  key: string;
  title: string;
  color: string;
  addCategoryId: number | null;
  totals: Totals;
  /**
   * What the card-level budget meter measures: every dépense of the card when the root category
   * itself has a budget (it covers its sub-categories), else the budgeted sections only.
   */
  budgetTotals: Totals;
  budget: number | null;
  /** A root budget always shows at card level; summed section budgets only when there are no section headings. */
  showCardMeter: boolean;
  sections: CardSection[];
  /** Section headings only help when the card mixes several (sub)categories. */
  showSectionTitles: boolean;
}

const UNCATEGORIZED_COLOR = '#e2e8f0';

@Component({
  selector: 'app-transactions',
  imports: [CommonModule, ReactiveFormsModule, MatchPickerComponent],
  templateUrl: './transactions.component.html',
  styleUrl: './transactions.component.css',
})
export class TransactionsComponent {
  private readonly transactionService = inject(TransactionService);
  private readonly accountService = inject(AccountService);
  private readonly categoryService = inject(CategoryService);
  private readonly budgetService = inject(BudgetService);
  private readonly fb = inject(FormBuilder);

  private readonly addDialog = viewChild.required<ElementRef<HTMLDialogElement>>('addDialog');

  protected readonly abs = Math.abs;
  protected readonly typeLabels = TRANSACTION_TYPE_LABELS;
  protected readonly types = Object.keys(TRANSACTION_TYPE_LABELS) as TransactionType[];

  protected readonly accounts = signal<Account[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly categoryTree = computed(() => buildCategoryTree(this.categories()));
  protected readonly budgets = signal<Budget[]>([]);
  protected readonly transactions = signal<Transaction[]>([]);
  protected readonly rowSort = signal<RowSort>('date-desc');

  protected readonly editingId = signal<number | null>(null);
  protected readonly pendingDeleteId = signal<number | null>(null);
  /** The row whose rapprochement picker is unfolded underneath it, if any. */
  protected readonly matchingId = signal<number | null>(null);
  protected readonly pendingConfirmAllCategories = signal(false);
  protected readonly pendingConfirmAllLabels = signal(false);
  protected readonly matchError = signal<string | null>(null);

  protected readonly month = signal(currentMonth());
  protected readonly filterAccountId = signal<number | null>(null);
  protected readonly filterCategoryId = signal<number | null>(null);
  protected readonly filterType = signal<TransactionType | null>(null);

  protected readonly monthLabel = computed(() => {
    const [year, month] = this.month().split('-').map(Number);
    return new Date(year, month - 1, 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  });

  /**
   * The real bank balance (initial + pointées) at the end of the selected month: the filtered
   * account's, or the sum of all when "Tous" is selected.
   */
  protected readonly bankBalance = computed(() => {
    const id = this.filterAccountId();
    const accounts = this.accounts();
    if (id === null) {
      return accounts.reduce((sum, account) => sum + account.balance, 0);
    }
    return accounts.find((account) => account.id === id)?.balance ?? 0;
  });

  protected readonly bankBalanceLabel = computed(() => {
    const id = this.filterAccountId();
    if (id === null) {
      return 'Solde total';
    }
    const name = this.accounts().find((account) => account.id === id)?.name;
    return name ? `Solde ${name}` : 'Solde';
  });

  /**
   * The month's previsionnelles for the selected account(s), fetched without the category/type
   * filters: the projected balance must not change just because the view is filtered.
   */
  private readonly accountPrevisionnelTotal = signal(0);

  /**
   * Month totals count previsionnelles too (they're the month's expected income/spending), and
   * transfers by direction: money coming into the account counts as revenue, money leaving it as
   * spending.
   */
  protected readonly kpis = computed(() => {
    const sum = (predicate: (t: Transaction) => boolean) =>
      this.transactions().filter(predicate).reduce((total, t) => total + t.amount, 0);
    const revenus = sum((t) => t.type === 'REVENU' || (t.type === 'TRANSFERT' && t.amount > 0));
    const depenses = sum((t) => t.type === 'DEPENSE' || (t.type === 'TRANSFERT' && t.amount < 0));
    return {
      revenus,
      depenses,
      solde: revenus + depenses,
      soldeApresPrevisionnel: this.bankBalance() + this.accountPrevisionnelTotal(),
    };
  });

  protected readonly previsionnelleTotal = computed(() =>
    this.transactions().filter((t) => t.status === 'PREVISIONNELLE').reduce((sum, t) => sum + t.amount, 0),
  );
  protected readonly pointeeTotal = computed(() =>
    this.transactions().filter((t) => t.status === 'POINTEE').reduce((sum, t) => sum + t.amount, 0),
  );

  protected readonly suggestedCategoryIds = computed(() =>
    this.transactions()
      .filter((t) => t.status === 'POINTEE' && t.categoryId !== null && !t.categoryConfirmed)
      .map((t) => t.id),
  );
  protected readonly suggestedLabelIds = computed(() =>
    this.transactions()
      .filter((t) => t.status === 'POINTEE' && !t.labelConfirmed)
      .map((t) => t.id),
  );

  /**
   * One card per root category (plus "Non catégorisées"), each split into sections by the exact
   * (sub)category. Order: uncategorized first (it needs attention), then income cards such as
   * "Crédit", then the rest by amount.
   */
  protected readonly cards = computed<CategoryCard[]>(() => {
    const byId = new Map(this.categories().map((c) => [c.id, c]));
    const budgetByCategory = new Map(this.budgets().map((b) => [b.categoryId, b.amount]));
    const rootOf = (category: Category): Category => {
      let current = category;
      while (current.parentId !== null && byId.has(current.parentId)) {
        current = byId.get(current.parentId)!;
      }
      return current;
    };
    const sumBudgets = (ids: number[]): number | null => {
      const known = ids.filter((id) => budgetByCategory.has(id));
      return known.length ? known.reduce((sum, id) => sum + budgetByCategory.get(id)!, 0) : null;
    };

    const groups = new Map<number, { root: Category | null; sections: Map<number, { category: Category | null; transactions: Transaction[] }> }>();
    for (const t of this.transactions()) {
      const leaf = t.categoryId !== null ? byId.get(t.categoryId) ?? null : null;
      const root = leaf ? rootOf(leaf) : null;
      const key = root?.id ?? 0;
      let group = groups.get(key);
      if (!group) {
        group = { root, sections: new Map() };
        groups.set(key, group);
      }
      const sectionKey = leaf?.id ?? 0;
      let section = group.sections.get(sectionKey);
      if (!section) {
        section = { category: leaf, transactions: [] };
        group.sections.set(sectionKey, section);
      }
      section.transactions.push(t);
    }

    const sort = this.rowSort();
    const cards = [...groups.values()].map(({ root, sections }) => {
      // ponytail: only a root's budget spans its sub-categories here; a budget on a mid-level
      // category still only covers its own section (no such budget exists today).
      const rootBudget = root ? budgetByCategory.get(root.id) ?? null : null;
      const sectionList: CardSection[] = [...sections.values()]
        .map(({ category, transactions }) => ({
          key: String(category?.id ?? 0),
          title: category === null ? 'Non catégorisées' : category.id === root?.id ? `${category.name} (direct)` : category.name,
          totals: totalsOf(transactions),
          // The root's budget is shown once, on the whole card - not on its "(direct)" section.
          budget: category && !(rootBudget !== null && category.id === root?.id) ? sumBudgets([category.id]) : null,
          transactions: sortRows(transactions, sort),
        }))
        .sort((a, b) => Math.abs(b.totals.net) - Math.abs(a.totals.net));
      const all = sectionList.flatMap((s) => s.transactions);
      // Only sections that carry a budget count against it - an unbudgeted sibling (e.g. Paramédical
      // next to a budgeted Pharmacie) must not show up as a budget overrun.
      const budgeted = sectionList.filter((s) => s.budget !== null).flatMap((s) => s.transactions);
      const revenue = all.filter((t) => t.type === 'REVENU').reduce((sum, t) => sum + t.amount, 0);
      const spending = all.filter((t) => t.type === 'DEPENSE').reduce((sum, t) => sum + t.amount, 0);
      const showSectionTitles = sectionList.length > 1 || (root !== null && sectionList[0].key !== String(root.id));
      const sectionsBudget = sumBudgets(sectionList.map((s) => Number(s.key)).filter((id) => id !== 0));
      return {
        key: String(root?.id ?? 0),
        title: root?.name ?? 'Non catégorisées',
        color: root?.color ?? UNCATEGORIZED_COLOR,
        addCategoryId: root?.id ?? null,
        totals: totalsOf(all),
        // Dépenses only, like the backend's budget "spent".
        budgetTotals: rootBudget !== null ? totalsOf(all.filter((t) => t.type === 'DEPENSE')) : totalsOf(budgeted),
        budget: rootBudget ?? sectionsBudget,
        showCardMeter: rootBudget !== null || (sectionsBudget !== null && !showSectionTitles),
        sections: sectionList,
        showSectionTitles,
        rank: root === null ? 0 : revenue > 0 && revenue >= Math.abs(spending) ? 1 : 2,
      };
    });
    return cards
      .sort((a, b) => a.rank - b.rank || Math.abs(b.totals.net) - Math.abs(a.totals.net))
      .map(({ rank, ...card }) => card);
  });

  private readonly wideScreen = signal(window.matchMedia('(min-width: 1000px)').matches);

  /**
   * Row-major placement: the first cards take the top of each column in priority order (so
   * "Non catégorisées" and "Crédit" both sit at the top), then each next card drops into the
   * shorter column. CSS columns would fill top-to-bottom and push "Crédit" down instead.
   */
  protected readonly cardColumns = computed<CategoryCard[][]>(() => {
    const cards = this.cards();
    if (!this.wideScreen()) {
      return [cards];
    }
    const columns: CategoryCard[][] = [[], []];
    const heights = [0, 0];
    for (const card of cards) {
      const target = heights[0] <= heights[1] ? 0 : 1;
      columns[target].push(card);
      heights[target] += 4 + card.totals.count + card.sections.length * (card.showSectionTitles ? 1.5 : 0);
    }
    return columns;
  });

  protected readonly form = this.fb.nonNullable.group({
    date: [this.today(), Validators.required],
    label: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    accountId: this.fb.control<number | null>(null, Validators.required),
    categoryId: this.fb.control<number | null>(null),
    type: this.fb.nonNullable.control<TransactionType>('DEPENSE'),
    transferAccountId: this.fb.control<number | null>(null),
    outgoing: this.fb.nonNullable.control<boolean>(true),
  });

  /** Populated per-row when a transaction is being edited in place inside its card. */
  protected readonly editForm = this.fb.nonNullable.group({
    date: ['', Validators.required],
    label: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    accountId: this.fb.control<number | null>(null, Validators.required),
    categoryId: this.fb.control<number | null>(null),
    type: this.fb.nonNullable.control<TransactionType>('DEPENSE'),
    transferAccountId: this.fb.control<number | null>(null),
    outgoing: this.fb.nonNullable.control<boolean>(true),
  });

  private defaultAccountId: number | null = null;

  constructor() {
    this.accountService.list().subscribe((accounts) => {
      this.accounts.set(accounts);
      this.defaultAccountId = accounts.find((a) => a.isDefault)?.id ?? null;
      this.form.controls.accountId.setValue(this.defaultAccountId);
      this.filterAccountId.set(this.defaultAccountId);
      this.reload();
    });
    this.categoryService.list().subscribe((categories) => this.categories.set(categories));
    const wide = window.matchMedia('(min-width: 1000px)');
    const onWidthChange = (event: MediaQueryListEvent) => this.wideScreen.set(event.matches);
    wide.addEventListener('change', onWidthChange);
    inject(DestroyRef).onDestroy(() => wide.removeEventListener('change', onWidthChange));
    // Picking a category that has a configured default transfer account pre-fills type/compte
    // cible - a suggestion only, the user can still change either field afterward.
    this.form.controls.categoryId.valueChanges.subscribe((categoryId) =>
      this.applyDefaultTransferAccount(this.form, categoryId),
    );
    this.editForm.controls.categoryId.valueChanges.subscribe((categoryId) =>
      this.applyDefaultTransferAccount(this.editForm, categoryId),
    );
  }

  private applyDefaultTransferAccount(form: typeof this.form, categoryId: number | null): void {
    const account = this.categories().find((c) => c.id === categoryId)?.defaultTransferAccountId;
    if (account != null) {
      form.patchValue({ type: 'TRANSFERT', transferAccountId: account });
    }
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  protected reload(): void {
    this.transactionService
      .search({
        month: this.month(),
        accountId: this.filterAccountId(),
        categoryId: this.filterCategoryId(),
        status: null,
        type: this.filterType(),
      })
      .subscribe((transactions) => this.transactions.set(transactions));
    this.transactionService
      .search({ month: this.month(), accountId: this.filterAccountId(), categoryId: null, status: 'PREVISIONNELLE', type: null })
      .subscribe((previsionnelles) =>
        this.accountPrevisionnelTotal.set(previsionnelles.reduce((total, t) => total + t.amount, 0)),
      );
    this.budgetService.listForMonth(this.month()).subscribe((budgets) => this.budgets.set(budgets));
    // Re-fetch balances too (as of the selected month's end), so the badge follows the month and
    // stays live as transactions are added/edited/deleted/matched.
    this.accountService.list(this.month()).subscribe((accounts) => this.accounts.set(accounts));
    // The suggested-categories/labels counts are about to change under them - don't leave a stale confirm prompt open.
    this.pendingConfirmAllCategories.set(false);
    this.pendingConfirmAllLabels.set(false);
  }

  protected onMonthChange(value: string): void {
    this.month.set(value);
    this.reload();
  }

  protected shiftMonth(delta: number): void {
    const [year, month] = this.month().split('-').map(Number);
    const date = new Date(year, month - 1 + delta, 1);
    this.onMonthChange(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`);
  }

  protected onFilterAccountChange(value: string): void {
    this.filterAccountId.set(value ? Number(value) : null);
    this.reload();
  }

  protected onFilterCategoryChange(value: string): void {
    this.filterCategoryId.set(value ? Number(value) : null);
    this.reload();
  }

  protected onFilterTypeChange(value: string): void {
    this.filterType.set((value as TransactionType) || null);
    this.reload();
  }

  protected onSortChange(value: string): void {
    this.rowSort.set(value as RowSort);
  }

  /** Opens the add dialog pre-filled with the card's category; the label input takes focus via autofocus. */
  protected openAdd(categoryId: number | null): void {
    // Always the account browsed on the page ("Tous" falls back to the default account).
    this.form.controls.accountId.setValue(this.filterAccountId() ?? this.defaultAccountId);
    this.form.controls.categoryId.setValue(categoryId);
    this.addDialog().nativeElement.showModal();
  }

  protected accountName(id: number | null): string {
    return this.accounts().find((a) => a.id === id)?.name ?? '';
  }

  protected closeAdd(): void {
    this.addDialog().nativeElement.close();
  }

  /** Closes on a backdrop click. Must return void: a handler evaluating to `false` makes Angular
   *  preventDefault() every click inside the dialog, which breaks its submit button. */
  protected onAddDialogClick(event: MouseEvent): void {
    if (event.target === this.addDialog().nativeElement) {
      this.closeAdd();
    }
  }

  protected edit(transaction: Transaction): void {
    this.pendingDeleteId.set(null);
    this.matchingId.set(null);
    this.editingId.set(transaction.id);
    // emitEvent: false - loading an existing (possibly TRANSFERT) transaction must not trigger
    // the category valueChanges handler and clobber its real transferAccountId/type with a
    // category's default.
    this.editForm.setValue(
      {
        date: transaction.date,
        label: transaction.label,
        amount: Math.abs(transaction.amount),
        accountId: transaction.accountId,
        categoryId: transaction.categoryId,
        type: transaction.type,
        transferAccountId: transaction.transferAccountId,
        outgoing: transaction.type !== 'TRANSFERT' || transaction.amount < 0,
      },
      { emitEvent: false },
    );
  }

  protected cancelEdit(): void {
    this.editingId.set(null);
  }

  protected setEditType(type: TransactionType): void {
    this.editForm.controls.type.setValue(type);
  }

  protected setEditOutgoing(outgoing: boolean): void {
    this.editForm.controls.outgoing.setValue(outgoing);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const request: TransactionRequest = { ...raw, accountId: raw.accountId! };
    this.transactionService.create(request).subscribe(() => {
      this.form.reset(
        {
          date: this.today(),
          label: '',
          amount: 0,
          accountId: this.defaultAccountId,
          categoryId: raw.categoryId,
          type: 'DEPENSE',
          transferAccountId: null,
          outgoing: true,
        },
        { emitEvent: false },
      );
      this.closeAdd();
      this.reload();
    });
  }

  protected submitEdit(): void {
    if (this.editForm.invalid) {
      return;
    }
    const id = this.editingId();
    if (id === null) {
      return;
    }
    const raw = this.editForm.getRawValue();
    const request: TransactionRequest = { ...raw, accountId: raw.accountId! };
    this.transactionService.update(id, request).subscribe(() => {
      this.editingId.set(null);
      this.reload();
    });
  }

  protected requestDelete(transactionId: number): void {
    this.editingId.set(null);
    this.pendingDeleteId.set(transactionId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(transactionId: number): void {
    this.transactionService.delete(transactionId).subscribe(() => {
      this.pendingDeleteId.set(null);
      this.reload();
    });
  }

  protected toggleMatching(transactionId: number): void {
    this.editingId.set(null);
    this.matchingId.set(this.matchingId() === transactionId ? null : transactionId);
  }

  /**
   * Candidates to reconcile with: same account, opposite status, not already matched. A pointee
   * that already absorbed a previsionnelle (transaction.matched) is done - it never gets more
   * candidates offered, since a second match would just be rejected by the backend anyway.
   */
  protected candidatesFor(transaction: Transaction): Transaction[] {
    if (transaction.matched) {
      return [];
    }
    return this.transactions().filter(
      (candidate) =>
        candidate.id !== transaction.id &&
        candidate.accountId === transaction.accountId &&
        candidate.status !== transaction.status &&
        !candidate.matched,
    );
  }

  /** Same-amount candidates: the ones worth flagging on the row itself, instead of on every unmatched line. */
  protected sameAmountCandidates(transaction: Transaction): Transaction[] {
    return this.candidatesFor(transaction).filter((candidate) => sameAmount(candidate.amount, transaction.amount));
  }

  /**
   * Suggestions are strictly one-to-one: a pair is only suggested when each line is the other's
   * single best match. Otherwise one line could be suggested to several others (e.g. one
   * previsionnelle offered to two bank lines at once), which reads as a reconciliation that
   * already belongs elsewhere. Lines already reconciled never take part (see candidatesFor).
   */
  private readonly mutualSuggestions = computed(() => {
    const best = new Map(this.transactions().map((t) => [t.id, this.bestMatchFor(t)]));
    const mutual = new Map<number, Transaction>();
    for (const [id, match] of best) {
      if (match && best.get(match.id)?.id === id) {
        mutual.set(id, match);
      }
    }
    return mutual;
  });

  protected suggestedMatchFor(transaction: Transaction): Transaction | null {
    return this.mutualSuggestions().get(transaction.id) ?? null;
  }

  /**
   * One side's best guess: the unique same-amount candidate, or - when several share that amount -
   * the unique candidate whose label best overlaps this one's. Null rather than guess on a tie.
   */
  private bestMatchFor(transaction: Transaction): Transaction | null {
    const candidates = this.sameAmountCandidates(transaction);
    if (candidates.length === 0) {
      return null;
    }
    if (candidates.length === 1) {
      return candidates[0];
    }

    let best: Transaction | null = null;
    let bestScore = -1;
    let tieCount = 0;
    for (const candidate of candidates) {
      const score = Math.max(
        labelSimilarity(transaction.label, candidate.label),
        labelSimilarity(candidate.label, transaction.label),
      );
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
        tieCount = 1;
      } else if (score === bestScore) {
        tieCount++;
      }
    }
    return bestScore > 0 && tieCount === 1 ? best : null;
  }

  protected budgetPercent(part: number, budget: number, total: number): number {
    return (Math.abs(part) / Math.max(budget, Math.abs(total))) * 100;
  }

  protected hasOriginalDivergence(transaction: Transaction): boolean {
    return (
      transaction.originalLabel !== null &&
      (transaction.originalLabel !== transaction.label || transaction.originalDate !== transaction.date)
    );
  }

  protected originalTooltip(transaction: Transaction): string {
    const date = transaction.originalDate ? this.formatDate(transaction.originalDate) : '';
    return `Import d’origine : ${date} — ${transaction.originalLabel}`;
  }

  private formatDate(isoDate: string): string {
    const [year, month, day] = isoDate.split('-');
    return `${day}/${month}/${year}`;
  }

  /** Accepts a single auto-suggested category, making it permanent. */
  protected confirmCategory(transaction: Transaction): void {
    this.matchError.set(null);
    this.transactionService.confirmCategory(transaction.id).subscribe({
      next: () => this.reload(),
      error: (err: HttpErrorResponse) => this.matchError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestConfirmAllCategories(): void {
    this.pendingConfirmAllCategories.set(true);
  }

  protected cancelConfirmAllCategories(): void {
    this.pendingConfirmAllCategories.set(false);
  }

  protected confirmAllCategories(): void {
    this.matchError.set(null);
    this.transactionService.confirmCategories(this.suggestedCategoryIds()).subscribe({
      next: () => {
        this.pendingConfirmAllCategories.set(false);
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.matchError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  /** Accepts a single auto-suggested label, making it permanent. */
  protected confirmLabel(transaction: Transaction): void {
    this.matchError.set(null);
    this.transactionService.confirmLabel(transaction.id).subscribe({
      next: () => this.reload(),
      error: (err: HttpErrorResponse) => this.matchError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  protected requestConfirmAllLabels(): void {
    this.pendingConfirmAllLabels.set(true);
  }

  protected cancelConfirmAllLabels(): void {
    this.pendingConfirmAllLabels.set(false);
  }

  protected confirmAllLabels(): void {
    this.matchError.set(null);
    this.transactionService.confirmLabels(this.suggestedLabelIds()).subscribe({
      next: () => {
        this.pendingConfirmAllLabels.set(false);
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.matchError.set(err.error?.message ?? 'Une erreur est survenue.'),
    });
  }

  /** Merges the matched previsionnelle(s) into their pointee counterpart; the previsionnelle row(s) disappear. */
  protected match(transaction: Transaction, matchWithIds: number[]): void {
    if (matchWithIds.length === 0) {
      return;
    }
    this.matchError.set(null);
    this.transactionService.match(transaction.id, matchWithIds).subscribe({
      next: () => {
        this.matchingId.set(null);
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.matchError.set(err.error?.message ?? 'Une erreur est survenue.');
        // The request can fail after the backend already committed (e.g. a dropped connection) -
        // reload so a stale, already-matched pointee doesn't look selectable for a retry.
        this.reload();
      },
    });
  }
}
