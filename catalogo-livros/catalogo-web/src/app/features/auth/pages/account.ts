import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { PERFIL_LABELS } from '../../../core/auth/auth.models';

@Component({
  imports: [DatePipe, RouterLink],
  template: `
    <section class="page">
      <div class="page-heading">
        <div>
          <h1>Minha conta</h1>
        </div>
      </div>
      <article class="panel account-card">
        <dl class="facts">
          <div>
            <dt>Nome</dt>
            <dd>{{ auth.session()?.nome ?? auth.session()?.email }}</dd>
          </div>
          <div>
            <dt>E-mail</dt>
            <dd>{{ auth.session()?.email }}</dd>
          </div>
          <div>
            <dt>Perfis</dt>
            <dd>{{ perfis() }}</dd>
          </div>
          <div>
            <dt>Sessão válida até</dt>
            <dd>{{ auth.session()?.expiresAt | date: 'dd/MM/yyyy, HH:mm' }}</dd>
          </div>
        </dl>
        <div class="actions">
          <a class="button-primary" routerLink="/emprestimos">Ver meus empréstimos</a>
          <button type="button" class="button-secondary" (click)="auth.logout()">Sair da conta</button>
        </div>
      </article>
    </section>
  `,
})
export class Account {
  protected readonly auth = inject(AuthService);

  protected perfis(): string {
    return (this.auth.session()?.perfis ?? []).map((perfil) => PERFIL_LABELS[perfil]).join(', ');
  }
}
