import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RecurringTransaction, RecurringTransactionRequest } from '../../core/models/recurring-transaction.model';

@Injectable({ providedIn: 'root' })
export class RecurringTransactionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/recurring-transactions`;

  list(): Observable<RecurringTransaction[]> {
    return this.http.get<RecurringTransaction[]>(this.baseUrl);
  }

  create(request: RecurringTransactionRequest): Observable<RecurringTransaction> {
    return this.http.post<RecurringTransaction>(this.baseUrl, request);
  }

  /** Whole definition, carried over to its prévisionnelles from the current month on. */
  update(id: number, request: RecurringTransactionRequest): Observable<RecurringTransaction> {
    return this.http.put<RecurringTransaction>(`${this.baseUrl}/${id}`, request);
  }

  /** New positive amount from the current month on (definition + its not-yet-pointed instances). */
  updateAmount(id: number, amount: number): Observable<RecurringTransaction> {
    return this.http.put<RecurringTransaction>(`${this.baseUrl}/${id}/amount`, { amount });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
