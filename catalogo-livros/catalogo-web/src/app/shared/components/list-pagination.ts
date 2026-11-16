import { Component, input, output } from '@angular/core';
import { PaginationState } from '../listing/paged-list';

@Component({
  selector: 'app-list-pagination',
  template: `
    <nav class="pagination" [attr.aria-label]="label()" [attr.aria-busy]="state().loading">
      <label class="pagination-size">
        Por página
        <select #pageSize (change)="sizeChange.emit(+pageSize.value)">
          @for (size of sizes; track size) {
            <option [value]="size" [selected]="size === state().size">{{ size }}</option>
          }
        </select>
      </label>

      <span class="pagination-total" aria-live="polite">
        {{ state().totalElements }} {{ state().totalElements === 1 ? 'resultado' : 'resultados' }}
      </span>

      <div class="pagination-controls">
        <button type="button" class="button-secondary small"
          [disabled]="state().loading || state().page === 0"
          (click)="pageChange.emit(state().page - 1)">Anterior</button>
        <span aria-live="polite">Página {{ state().page + 1 }} de {{ state().totalPages }}</span>
        <button type="button" class="button-secondary small"
          [disabled]="state().loading || state().page + 1 >= state().totalPages"
          (click)="pageChange.emit(state().page + 1)">Próxima</button>
      </div>
    </nav>
  `,
})
export class ListPagination {
  readonly label = input.required<string>();
  readonly state = input.required<PaginationState>();
  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();
  protected readonly sizes = [12, 20, 50];
}
