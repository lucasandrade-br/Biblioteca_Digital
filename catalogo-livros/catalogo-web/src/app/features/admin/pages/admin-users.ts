import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { PERFIL_LABELS, Perfil } from '../../../core/auth/auth.models';
import { Observable } from 'rxjs';
import { ListFilters } from '../../../shared/components/list-filters';
import { ListPagination } from '../../../shared/components/list-pagination';
import { createPagedList } from '../../../shared/listing/paged-list';
import { apiErrorMessage } from '../../../core/http/api-error';
import { AdminApi, AdminUser } from '../data-access/admin-api';

@Component({
  imports: [ReactiveFormsModule, ListFilters, ListPagination],
  template: `
    <section class="page">
      <div class="page-heading">
        <div>
          <span class="section-kicker">ADMINISTRAÇÃO</span>
          <h1>Usuários</h1>
          <p class="page-intro">Defina bibliotecários e inative contas. Novos menus aparecem no próximo login da pessoa.</p>
        </div>
      </div>

      <form appListFilters [formGroup]="filters" (ngSubmit)="list.apply(filters.getRawValue())" (clear)="clearFilters()">
        <div class="field">
          <label for="q">Buscar</label>
          <input id="q" type="search" formControlName="q" placeholder="Nome ou e-mail" />
        </div>
        <div class="field">
          <label for="perfil">Perfil</label>
          <select id="perfil" formControlName="perfil">
            <option [ngValue]="null">Todos</option>
            @for (role of profileOptions; track role) {
              <option [ngValue]="role">{{ profileLabels[role] }}</option>
            }
          </select>
        </div>
        <div class="field">
          <label for="ativo">Situação</label>
          <select id="ativo" formControlName="ativo">
            <option [ngValue]="null">Todas</option>
            <option [ngValue]="true">Ativo</option>
            <option [ngValue]="false">Inativo</option>
          </select>
        </div>
      </form>

      @if (message(); as current) {
        <div class="notice" [class.success]="current.ok" [class.error]="!current.ok" role="status">{{ current.text }}</div>
      }

      @if (list.loading()) {
        <p class="page-message" role="status">Carregando usuários...</p>
      } @else if (list.error()) {
        <div class="notice error" role="alert">{{ list.error() }}</div>
      } @else if (!list.items().length) {
        <div class="empty-state"><h2>Nenhum usuário encontrado.</h2></div>
      } @else {
        <div class="table-wrap panel">
          <table>
            <thead>
              <tr><th>Nome</th><th>E-mail</th><th>Perfis</th><th>Situação</th><th></th></tr>
            </thead>
            <tbody>
              @for (user of list.items(); track user.id) {
                <tr>
                  <td>{{ user.nome }}</td>
                  <td>{{ user.email }}</td>
                  <td>{{ roles(user) }}</td>
                  <td>
                    <span [class]="'status-badge status-' + (user.ativo ? 'active' : 'cancelled')">
                      {{ user.ativo ? 'Ativo' : 'Inativo' }}
                    </span>
                  </td>
                  <td class="row-actions">
                    @if (user.perfis.includes('LIBRARIAN')) {
                      <button type="button" class="button-secondary small" (click)="revoke(user)">Remover bibliotecário</button>
                    } @else {
                      <button type="button" class="button-secondary small" (click)="grant(user)">Tornar bibliotecário</button>
                    }
                    @if (user.ativo) {
                      <button type="button" class="button-secondary small" (click)="deactivate(user)">Inativar</button>
                    } @else {
                      <button type="button" class="button-secondary small" (click)="activate(user)">Reativar</button>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
      @if (!list.error()) {
        <app-list-pagination label="Paginação de usuários" [state]="list.pagination()"
          (pageChange)="list.load($event)" (sizeChange)="list.resize($event)" />
      }
    </section>
  `,
})
export class AdminUsers implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly profileLabels = PERFIL_LABELS;
  protected readonly profileOptions = Object.keys(PERFIL_LABELS) as Perfil[];
  protected readonly filters = inject(FormBuilder).nonNullable.group({
    q: [''],
    perfil: [null as Perfil | null],
    ativo: [null as boolean | null],
  });
  protected readonly list = createPagedList(
    this.filters.getRawValue(),
    (filter, page, size) => this.api.list(filter, page, size),
    () => 'Não foi possível carregar os usuários.',
  );
  protected readonly message = signal<{ ok: boolean; text: string } | null>(null);

  ngOnInit(): void {
    this.list.load(0);
  }

  protected clearFilters(): void {
    this.filters.reset();
    this.list.apply(this.filters.getRawValue());
  }

  protected roles(user: AdminUser): string {
    return user.perfis.map((perfil) => PERFIL_LABELS[perfil]).join(', ');
  }

  protected grant(user: AdminUser): void {
    this.run(this.api.grantLibrarian(user.id), `${user.nome} agora é bibliotecário.`);
  }

  protected revoke(user: AdminUser): void {
    this.run(this.api.revokeLibrarian(user.id), `${user.nome} não é mais bibliotecário.`);
  }

  protected deactivate(user: AdminUser): void {
    this.run(this.api.deactivate(user.id), `${user.nome} foi inativado.`);
  }

  protected activate(user: AdminUser): void {
    this.run(this.api.activate(user.id), `${user.nome} foi reativado.`);
  }

  private run(request: Observable<AdminUser>, success: string): void {
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.list.items.update((users) => users.map((user) => (user.id === updated.id ? updated : user)));
        this.message.set({ ok: true, text: success });
        const filter = this.list.filter();
        if (filter.perfil || filter.ativo !== null) this.list.refresh();
      },
      error: (error: unknown) => {
        this.message.set({ ok: false, text: apiErrorMessage(error, 'Não foi possível concluir a operação.') });
      },
    });
  }
}
