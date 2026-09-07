import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Account, AccountRequest } from '../../core/models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/accounts`;

  list(): Observable<Account[]> {
    return this.http.get<Account[]>(this.baseUrl);
  }

  create(request: AccountRequest): Observable<Account> {
    return this.http.post<Account>(this.baseUrl, request);
  }

  update(id: number, request: AccountRequest): Observable<Account> {
    return this.http.put<Account>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
