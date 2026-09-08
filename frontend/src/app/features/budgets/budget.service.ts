import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Budget, BudgetRequest } from '../../core/models/budget.model';

@Injectable({ providedIn: 'root' })
export class BudgetService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/budgets`;

  listForMonth(month: string): Observable<Budget[]> {
    const params = new HttpParams().set('month', month);
    return this.http.get<Budget[]>(this.baseUrl, { params });
  }

  create(request: BudgetRequest): Observable<Budget> {
    return this.http.post<Budget>(this.baseUrl, request);
  }

  update(id: number, request: BudgetRequest): Observable<Budget> {
    return this.http.put<Budget>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
