import { computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, catchError, of, switchMap } from 'rxjs';
import { apiErrorMessage } from '../../core/http/api-error';
import { Page } from '../../core/http/page';

export interface PaginationState {
  page: number;
  size: number;
  loading: boolean;
  totalPages: number;
  totalElements: number;
}

export function createPagedList<T, F>(
  initialFilter: F,
  find: (filter: F, page: number, size: number) => Observable<Page<T>>,
  errorMessage: () => string,
  defaultSize = 20,
) {
  const filter = signal(initialFilter);
  const items = signal<T[]>([]);
  const page = signal(0);
  const size = signal(defaultSize);
  const totalPages = signal(1);
  const totalElements = signal(0);
  const loading = signal(true);
  const error = signal('');
  let requestedPage = 0;
  const requests = new Subject<{ filter: F; page: number; size: number }>();

  requests.pipe(
    switchMap((request) => find(request.filter, request.page, request.size).pipe(
      switchMap((result) => {
        const lastPage = Math.max(0, result.totalPages - 1);
        return request.page > lastPage ? find(request.filter, lastPage, request.size) : of(result);
      }),
      catchError((failure: unknown) => {
        error.set(apiErrorMessage(failure, errorMessage()));
        loading.set(false);
        return EMPTY;
      }),
    )),
    takeUntilDestroyed(),
  ).subscribe((result) => {
    items.set(result.content);
    requestedPage = result.number;
    page.set(result.number);
    totalPages.set(Math.max(1, result.totalPages));
    totalElements.set(result.totalElements);
    loading.set(false);
  });

  function load(targetPage: number): void {
    requestedPage = targetPage;
    loading.set(true);
    error.set('');
    requests.next({ filter: filter(), page: targetPage, size: size() });
  }

  return {
    items,
    filter: filter.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    pagination: computed<PaginationState>(() => ({
      page: page(), size: size(), loading: loading(), totalPages: totalPages(), totalElements: totalElements(),
    })),
    load,
    apply(value: F): void {
      filter.set(value);
      page.set(0);
      load(0);
    },
    resize(value: number): void {
      size.set(value);
      page.set(0);
      load(0);
    },
    refresh(): void {
      load(requestedPage);
    },
  };
}
