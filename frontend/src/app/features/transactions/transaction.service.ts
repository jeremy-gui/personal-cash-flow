import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Transaction,
  TransactionFilters,
  TransactionRequest,
} from '../../core/models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/transactions`;

  search(filters: TransactionFilters): Observable<Transaction[]> {
    let params = new HttpParams();
    if (filters.month) {
      params = params.set('month', filters.month);
    }
    if (filters.accountId !== null) {
      params = params.set('accountId', filters.accountId);
    }
    if (filters.categoryId !== null) {
      params = params.set('categoryId', filters.categoryId);
    }
    if (filters.status !== null) {
      params = params.set('status', filters.status);
    }
    if (filters.type !== null) {
      params = params.set('type', filters.type);
    }
    return this.http.get<Transaction[]>(this.baseUrl, { params });
  }

  create(request: TransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(this.baseUrl, request);
  }

  update(id: number, request: TransactionRequest): Observable<Transaction> {
    return this.http.put<Transaction>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  match(id: number, matchWithIds: number[]): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.baseUrl}/${id}/match`, { matchWithIds });
  }
}
