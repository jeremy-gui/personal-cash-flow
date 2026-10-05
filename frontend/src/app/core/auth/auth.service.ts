import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CurrentUser } from '../models/user.model';

/** Session-cookie auth: the backend sets the cookies, Angular sends the XSRF-TOKEN back on its own. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/auth`;

  readonly currentUser = signal<CurrentUser | null>(null);
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ADMIN');

  /** Null when not logged in (the backend answers 401). */
  loadMe(): Observable<CurrentUser | null> {
    return this.http.get<CurrentUser>(`${this.baseUrl}/me`).pipe(
      catchError(() => of(null)),
      tap((user) => this.currentUser.set(user)),
    );
  }

  login(username: string, password: string, rememberMe: boolean): Observable<CurrentUser | null> {
    let body = new HttpParams().set('username', username).set('password', password);
    if (rememberMe) {
      body = body.set('remember-me', 'true');
    }
    return this.http.post<void>(`${this.baseUrl}/login`, body).pipe(switchMap(() => this.loadMe()));
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, null).pipe(tap(() => this.currentUser.set(null)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/password`, { currentPassword, newPassword });
  }
}
