import { Component, Input, computed, signal } from '@angular/core';

const SPINES = ['#1f3a4d', '#7a3b2e', '#3f5a3c', '#5b4670', '#8a6a24', '#2f5f66'];

@Component({
  selector: 'app-book-cover',
  template: `
    @if (url && !failed()) {
      <img class="book-cover" [src]="url" [alt]="'Capa de ' + title" loading="lazy" (error)="failed.set(true)" />
    } @else {
      <div class="book-cover book-cover-drawn" [style.background]="color()" aria-hidden="true">
        <strong>{{ title }}</strong>
        <small>{{ author }}</small>
      </div>
    }
  `,
})
export class BookCover {
  private readonly titleText = signal('');

  @Input({ required: true }) set title(value: string) {
    this.titleText.set(value);
  }
  get title(): string {
    return this.titleText();
  }
  @Input() author = '';
  @Input() url: string | null = null;

  protected readonly failed = signal(false);
  protected readonly color = computed(() => {
    const sum = [...this.titleText()].reduce((total, char) => total + char.charCodeAt(0), 0);
    return SPINES[sum % SPINES.length];
  });
}
