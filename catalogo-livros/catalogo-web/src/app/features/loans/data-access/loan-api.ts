import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { API_URL } from '../../../core/http/api-url';
import { Page } from '../../../core/http/page';
import { Loan, LoanFilter, LoanScope } from '../models/loan.models';

@Injectable({ providedIn: 'root' })
export class LoanApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  reserve(bookId: number, idempotencyKey: string) {
    return this.http.post<Loan>(`${this.api}/emprestimos`, { bookId }, {
      headers: new HttpHeaders({ 'Idempotency-Key': idempotencyKey }),
    });
  }

  listMine(filter: LoanFilter & { scope: LoanScope }, page: number, size: number) {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'requestedAt,desc');
    params = params.set('scope', filter.scope);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.q.trim()) params = params.set('q', filter.q.trim());
    return this.http.get<Page<Loan>>(`${this.api}/emprestimos`, { params });
  }

  cancel(id: number) {
    return this.http.patch<Loan>(`${this.api}/emprestimos/${id}/cancelar`, {});
  }

  renew(id: number) {
    return this.http.patch<Loan>(`${this.api}/emprestimos/${id}/renovar`, {});
  }

  payFine(id: number) {
    return this.http.post<Loan>(`${this.api}/emprestimos/${id}/multa/pagamento`, {});
  }
}
