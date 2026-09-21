import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ImportSummary } from '../../core/models/import.model';

@Injectable({ providedIn: 'root' })
export class ImportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/imports`;

  upload(file: File): Observable<ImportSummary> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportSummary>(this.baseUrl, formData);
  }

  uploadCsv(accountId: number, file: File): Observable<ImportSummary> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportSummary>(`${this.baseUrl}/csv`, formData, { params: { accountId } });
  }

  history(): Observable<ImportSummary[]> {
    return this.http.get<ImportSummary[]>(this.baseUrl);
  }
}
