import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Provision,
  ProvisionContribution,
  ProvisionContributionRequest,
  ProvisionRequest,
} from '../../core/models/provision.model';

@Injectable({ providedIn: 'root' })
export class ProvisionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/provisions`;

  list(): Observable<Provision[]> {
    return this.http.get<Provision[]>(this.baseUrl);
  }

  create(request: ProvisionRequest): Observable<Provision> {
    return this.http.post<Provision>(this.baseUrl, request);
  }

  update(id: number, request: ProvisionRequest): Observable<Provision> {
    return this.http.put<Provision>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  listContributions(provisionId: number): Observable<ProvisionContribution[]> {
    return this.http.get<ProvisionContribution[]>(`${this.baseUrl}/${provisionId}/contributions`);
  }

  addContribution(provisionId: number, request: ProvisionContributionRequest): Observable<ProvisionContribution> {
    return this.http.post<ProvisionContribution>(`${this.baseUrl}/${provisionId}/contributions`, request);
  }

  deleteContribution(provisionId: number, contributionId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${provisionId}/contributions/${contributionId}`);
  }
}
