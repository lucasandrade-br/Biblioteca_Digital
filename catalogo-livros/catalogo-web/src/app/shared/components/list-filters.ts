import { Component, output } from '@angular/core';

@Component({
  selector: 'form[appListFilters]',
  host: { class: 'filter-bar list-filter-bar', role: 'search' },
  template: `
    <ng-content />
    <div class="list-filter-actions">
      <button type="submit" class="button-primary">Buscar</button>
      <button type="button" class="button-secondary" (click)="clear.emit()">Limpar filtros</button>
    </div>
  `,
})
export class ListFilters {
  readonly clear = output<void>();
}
