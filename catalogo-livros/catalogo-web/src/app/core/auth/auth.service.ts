import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { API_URL } from '../http/api-url';
import { AuthResponse, CreateUser, Credentials, Perfil, Session } from './auth.models';

export const SESSION_KEY = 'catalogo.session';

export function readSession(token: string): Session | null {
  try {
    if (token.split('.').length !== 3) return null;
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const bytes = Uint8Array.from(atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')), (c) =>
      c.charCodeAt(0),
    );
    const claims = JSON.parse(new TextDecoder().decode(bytes));
    if (
      typeof claims.sub !== 'string' ||
      !claims.sub ||
      claims.iss !== 'catalogo-api' ||
      typeof claims.exp !== 'number' ||
      !Number.isFinite(claims.exp) ||
      claims.exp * 1000 <= Date.now()
    )
      return null;
    return {
      token,
      email: claims.sub,
      nome: typeof claims.nome === 'string' ? claims.nome.trim() || null : null,
      perfis: Array.isArray(claims.perfis) ? claims.perfis : [],
      expiresAt: claims.exp * 1000,
    };
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = inject(API_URL);
  private readonly currentSession = signal<Session | null>(null);
  readonly session = this.currentSession.asReadonly();
  readonly isStaff = computed(() => this.hasRole('LIBRARIAN') || this.hasRole('ADMIN'));
  private expiryTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    try {
      const token = sessionStorage.getItem(SESSION_KEY);
      const session = token ? readSession(token) : null;
      if (session) this.setSession(session);
      else sessionStorage.removeItem(SESSION_KEY);
    } catch {
      /* Sem storage, a sessão fica em memória. */
    }
  }

  login(credentials: Credentials) {
    return this.http.post<AuthResponse>(`${this.api}/auth/login`, credentials).pipe(
      tap((response) => {
        const session = readSession(response.token);
        if (!session) throw new Error('A resposta de acesso é inválida. Tente entrar novamente.');
        this.setSession(session);
      }),
    );
  }

  activate(request: { email: string; code: string; password: string }) {
    return this.http.post<void>(`${this.api}/auth/ativar`, request);
  }

  register(user: CreateUser) {
    return this.http.post<void>(`${this.api}/usuarios`, user);
  }

  hasRole(perfil: Perfil): boolean {
    return this.session()?.perfis.includes(perfil) ?? false;
  }

  isAuthenticated(): boolean {
    const session = this.session();
    if (session && session.expiresAt > Date.now()) return true;
    this.clearSession();
    return false;
  }

  clearSession(): void {
    clearTimeout(this.expiryTimer);
    this.currentSession.set(null);
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      /* Storage pode estar desabilitado. */
    }
  }

  logout(): void {
    this.clearSession();
    void this.router.navigate(['/entrar']);
  }

  expireSession(): void {
    this.clearSession();
    void this.router.navigate(['/entrar'], { queryParams: { sessao: 'expirada' } });
  }

  private setSession(session: Session): void {
    clearTimeout(this.expiryTimer);
    this.currentSession.set(session);
    try {
      sessionStorage.setItem(SESSION_KEY, session.token);
    } catch {
      /* Fica em memória. */
    }
    this.expiryTimer = setTimeout(
      () => this.expireSession(),
      Math.min(session.expiresAt - Date.now(), 2147483647),
    );
  }

  ngOnDestroy(): void {
    clearTimeout(this.expiryTimer);
  }
}
