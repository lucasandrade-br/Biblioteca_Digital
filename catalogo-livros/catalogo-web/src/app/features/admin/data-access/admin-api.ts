import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_URL } from '../../../core/http/api-url';
import { Perfil } from '../../../core/auth/auth.models';
import { Page } from '../../../core/http/page';

export interface AdminUser {
  id: number;
  nome: string;
  email: string;
  ativo: boolean;
  perfis: Perfil[];
}

export interface UserFilter {
  q: string;
  perfil: Perfil | null;
  ativo: boolean | null;
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);

  list(filter: UserFilter, page: number, size: number) {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'nome,asc');
    if (filter.q.trim()) params = params.set('q', filter.q.trim());
    if (filter.perfil) params = params.set('perfil', filter.perfil);
    if (filter.ativo !== null) params = params.set('ativo', filter.ativo);
    return this.http.get<Page<AdminUser>>(`${this.api}/admin/usuarios`, { params });
  }

  grantLibrarian(id: number) {
    return this.http.put<AdminUser>(`${this.api}/admin/usuarios/${id}/bibliotecario`, {});
  }

  revokeLibrarian(id: number) {
    return this.http.delete<AdminUser>(`${this.api}/admin/usuarios/${id}/bibliotecario`);
  }

  deactivate(id: number) {
    return this.http.patch<AdminUser>(`${this.api}/admin/usuarios/${id}/inativar`, {});
  }

  activate(id: number) {
    return this.http.patch<AdminUser>(`${this.api}/admin/usuarios/${id}/reativar`, {});
  }
}
