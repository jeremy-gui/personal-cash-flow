import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, output, signal } from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { Transaction } from '../../core/models/transaction.model';

function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

/** Label + date + amount (both "10.99" and "10,99") so searching by amount works with either decimal separator. */
function searchHaystack(candidate: Transaction): string {
  const magnitude = Math.abs(candidate.amount).toFixed(2);
  return normalizeText(`${candidate.label} ${candidate.date} ${magnitude} ${magnitude.replace('.', ',')}`);
}

/** Every typed word must appear somewhere in the candidate, in any order - an approximate match rather than one exact phrase. */
function matchesSearch(candidate: Transaction, term: string): boolean {
  const tokens = normalizeText(term.trim()).split(/\s+/).filter(Boolean);
  if (tokens.length === 0) {
    return true;
  }
  const haystack = searchHaystack(candidate);
  return tokens.every((token) => haystack.includes(token));
}

/** Searchable dropdown for picking a rapprochement candidate, with an optional pre-selected suggestion. */
@Component({
  selector: 'app-match-picker',
  imports: [CommonModule, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option],
  templateUrl: './match-picker.component.html',
  styleUrl: './match-picker.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MatchPickerComponent {
  candidates = input.required<Transaction[]>();
  suggested = input<Transaction | null>(null);
  /** When true, several candidates can be picked at once (a pointee absorbing several previsionnelles). */
  multi = input(false);
  /** The row's own amount - used in multi mode to warn when the selection doesn't sum up to it. */
  targetAmount = input(0);
  matched = output<number[]>();

  protected readonly searchTerm = signal('');
  protected readonly expanded = signal(false);

  /**
   * The table's own scroll container clips a plain `position: absolute` popup, so the dropdown is
   * `position: fixed` instead, anchored here from the input's on-screen position at open time.
   */
  protected readonly popupPosition = signal<{ top: number; left: number; width: number } | null>(null);

  /**
   * ngListbox's value model is always array-typed even for single selection, and the directive
   * writes to it just from opening the popup (it auto-highlights an item for keyboard nav) - not
   * only from a deliberate pick. So this tracks what's live-highlighted while browsing, not what's
   * confirmed; see `committedId` for the latter.
   */
  protected readonly selectedIds = linkedSignal<number[]>(() => {
    const suggestedId = this.suggested()?.id;
    return suggestedId === undefined ? [] : [suggestedId];
  });

  /** The actually-confirmed pick: only updated by an explicit click/Enter (see `closePopup`), never by merely opening or browsing the popup. */
  protected readonly committedId = linkedSignal<number | null>(() => this.suggested()?.id ?? null);

  protected readonly selectedCandidate = computed(
    () => this.candidates().find((candidate) => candidate.id === this.committedId()) ?? null,
  );

  /** Multi mode: selectedIds IS the deliberate selection - explicit selectionMode rules out the
   *  open-menu auto-highlight that made committedId necessary for single-select. */
  protected readonly selectedTotal = computed(() => {
    const ids = new Set(this.selectedIds());
    return this.candidates()
      .filter((candidate) => ids.has(candidate.id))
      .reduce((sum, candidate) => sum + candidate.amount, 0);
  });

  protected readonly amountMismatch = computed(
    () => this.selectedIds().length > 0 && Math.round(this.selectedTotal() * 100) !== Math.round(this.targetAmount() * 100),
  );

  protected readonly filteredCandidates = computed(() => {
    const term = this.searchTerm();
    const candidates = this.candidates();
    if (!term.trim()) {
      return candidates;
    }
    return candidates.filter((candidate) => matchesSearch(candidate, term));
  });

  protected optionText(candidate: Transaction): string {
    const [, month, day] = candidate.date.split('-');
    return `${day}/${month} — ${candidate.label} (${candidate.amount.toFixed(2)} €)`;
  }

  protected isSuggested(candidate: Transaction): boolean {
    return candidate.id === this.suggested()?.id;
  }

  protected openPopup(event: FocusEvent): void {
    const target = event.target as HTMLElement;
    const rect = target.getBoundingClientRect();
    this.popupPosition.set({ top: rect.bottom + 4, left: rect.left, width: Math.max(rect.width, 320) });
    this.expanded.set(true);
  }

  /**
   * Fired on an explicit click/Enter on an option - promotes the live highlight into the
   * confirmed pick. In multi mode the popup stays open so several options can be picked in a
   * row; selectedIds already reflects the deliberate choice there (see `selectedTotal`).
   */
  protected closePopup(): void {
    if (this.multi()) {
      return;
    }
    this.committedId.set(this.selectedIds()[0] ?? null);
    this.expanded.set(false);
  }

  protected confirm(): void {
    if (this.multi()) {
      const ids = this.selectedIds();
      if (ids.length > 0) {
        this.matched.emit(ids);
        this.expanded.set(false);
      }
      return;
    }
    const id = this.committedId();
    if (id !== null) {
      this.matched.emit([id]);
    }
  }
}
