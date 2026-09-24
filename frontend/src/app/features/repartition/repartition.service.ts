import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RepartitionLine, RepartitionLineRequest } from '../../core/models/repartition.model';

@Injectable({ providedIn: 'root' })
export class RepartitionService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/repartition-lines`;

  list(): Observable<RepartitionLine[]> {
    return this.http.get<RepartitionLine[]>(this.baseUrl);
  }

  create(request: RepartitionLineRequest): Observable<RepartitionLine> {
    return this.http.post<RepartitionLine>(this.baseUrl, request);
  }

  update(id: number, request: RepartitionLineRequest): Observable<RepartitionLine> {
    return this.http.put<RepartitionLine>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
