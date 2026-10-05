import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();
  return (user ? of(user) : auth.loadMe()).pipe(
    map((u) => u ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } })),
  );
};

/** Runs under authGuard (child route), so the current user is already loaded. */
export const adminGuard: CanActivateFn = () =>
  inject(AuthService).isAdmin() || inject(Router).createUrlTree(['/dashboard']);
