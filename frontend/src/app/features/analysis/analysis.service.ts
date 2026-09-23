import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { YearlyAnalysis } from '../../core/models/analysis.model';

@Injectable({ providedIn: 'root' })
export class AnalysisService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/analysis`;

  yearly(year: number): Observable<YearlyAnalysis> {
    const params = new HttpParams().set('year', year);
    return this.http.get<YearlyAnalysis>(`${this.baseUrl}/yearly`, { params });
  }
}
