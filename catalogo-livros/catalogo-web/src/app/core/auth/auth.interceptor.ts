import { inject } from '@angular/core';
import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { API_URL } from '../http/api-url';
import { AuthService } from './auth.service';

export const VERIFY_SESSION_ON_UNAUTHORIZED = new HttpContextToken(() => false);

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const api = inject(API_URL);
  const isApi = request.url === api || request.url.startsWith(`${api}/`);
  const path = request.url.split('?')[0];
  const isPublic =
    path === `${api}/auth/login` ||
    (path === `${api}/usuarios` && request.method === 'POST') ||
    (request.method === 'GET' &&
      (path === `${api}/livros` ||
        (path.startsWith(`${api}/livros/`) && !path.startsWith(`${api}/livros/acervo`)) ||
        path === `${api}/generos` ||
        path === `${api}/autores`));
  const session = isApi && !isPublic && auth.isAuthenticated() ? auth.session() : null;
  const outgoing = session
    ? request.clone({ setHeaders: { Authorization: `Bearer ${session.token}` } })
    : request;
  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (isApi && !isPublic && error instanceof HttpErrorResponse && error.status === 401 &&
          !request.context.get(VERIFY_SESSION_ON_UNAUTHORIZED))
        auth.expireSession();
      return throwError(() => error);
    }),
  );
};
