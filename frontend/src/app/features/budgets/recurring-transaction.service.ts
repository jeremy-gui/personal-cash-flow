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

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
