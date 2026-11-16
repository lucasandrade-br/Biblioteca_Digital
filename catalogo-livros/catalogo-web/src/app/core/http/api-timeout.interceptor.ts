import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TimeoutError, mergeMap, takeUntil, throwError, timer } from 'rxjs';
import { API_URL } from './api-url';

const API_TIMEOUT_MS = 15_000;

export const apiTimeoutInterceptor: HttpInterceptorFn = (request, next) => {
  const api = inject(API_URL);
  const path = request.url.split('?')[0];
  if (path !== api && !path.startsWith(`${api}/`)) return next(request);

  // A contagem inclui a espera pela resposta inteira, mesmo quando há eventos de progresso.
  const deadline = timer(API_TIMEOUT_MS).pipe(mergeMap(() => throwError(() => new TimeoutError())));
  return next(request).pipe(takeUntil(deadline));
};
