import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { API_URL } from '../../../core/http/api-url';
import { Page } from '../../../core/http/page';
import { Loan, LoanFilter } from '../../loans/models/loan.models';

export type Deadline = 'TODOS' | 'ATRASO' | 'HOJE' | 'PROXIMOS_7';

export interface DeskFilter extends LoanFilter {
  pending: boolean;
  deadline: Deadline;
  from: string;
  to: string;
}

export interface DeskReader {
  id: number;
  nome: string;
  email: string;
  ativo: boolean;
  activationPending: boolean;
}

export interface Enrollment {
  reader: DeskReader;
  activationCode: string | null;
  expiresAt: string | null;
}

export interface Preparation {
  reservation: Loan | null;
  availableCopies: number;
}

export interface DeskSummary {
  reservedCount: number;
  activeCount: number;
  overdueCount: number;
  pendingFinesInCents: number;
  paidFinesInCents: number;
}

@Injectable({ providedIn: 'root' })
export class DeskApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  summary() {
    return this.http.get<DeskSummary>(`${this.api}/balcao/resumo`);
  }

  list(filter: DeskFilter, page: number, size: number) {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'updatedAt,desc');
    if (filter.status) params = params.set('status', filter.status);
    if (filter.q.trim()) params = params.set('q', filter.q.trim());
    if (filter.pending) params = params.set('pending', true);
    if (filter.deadline !== 'TODOS') params = params.set('deadline', filter.deadline);
    if (filter.from) params = params.set('from', filter.from);
    if (filter.to) params = params.set('to', filter.to);
    return this.http.get<Page<Loan>>(`${this.api}/balcao/emprestimos`, { params });
  }

  findByCode(code: string) {
    return this.http.get<Loan>(`${this.api}/balcao/retiradas/${encodeURIComponent(code)}`);
  }

  confirmPickup(pickupCode: string) {
    return this.http.post<Loan>(`${this.api}/balcao/retiradas`, { pickupCode });
  }

  searchReaders(q: string, page: number, size: number) {
    return this.http.get<Page<DeskReader>>(`${this.api}/balcao/leitores`, { params: { q, page, size } });
  }

  enroll(reader: { nome: string; email: string }) {
    return this.http.post<Enrollment>(`${this.api}/balcao/leitores`, reader);
  }

  reissue(readerId: number) {
    return this.http.post<Enrollment>(`${this.api}/balcao/leitores/${readerId}/codigo-ativacao`, {});
  }

  prepare(readerId: number, bookId: number) {
    return this.http.get<Preparation>(`${this.api}/balcao/emprestimos/preparar`, {
      params: { readerId, bookId },
    });
  }

  lend(readerId: number, bookId: number, key: string) {
    return this.http.post<Loan>(`${this.api}/balcao/emprestimos`, { readerId, bookId }, {
      headers: new HttpHeaders({ 'Idempotency-Key': key }),
    });
  }

  confirmAssistedPickup(id: number) {
    return this.http.post<Loan>(`${this.api}/balcao/emprestimos/${id}/retirada-assistida`, {});
  }

  registerReturn(loanId: number) {
    return this.http.post<Loan>(`${this.api}/balcao/emprestimos/${loanId}/devolucao`, {});
  }
}
