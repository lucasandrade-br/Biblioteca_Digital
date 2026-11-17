import { Component, ElementRef, inject, input, signal } from '@angular/core';
import { IconName, InterfaceIcon } from './interface-icon';

const TONES: Record<RowActionKind, string> = {
  edit: 'blue',
  delete: 'red',
  deactivate: 'ochre',
  activate: 'green',
  grant: 'green',
  revoke: 'ochre',
  return: 'teal',
  pickup: 'blue',
};

type RowActionKind = Exclude<IconName, 'books' | 'operations' | 'system' | 'menu'>;

@Component({
  selector: 'button[appRowAction], a[appRowAction]',
  imports: [InterfaceIcon],
  host: {
    class: 'row-action',
    '[attr.aria-label]': 'label()',
    '[attr.data-tone]': 'tones[appRowAction()]',
    '(mouseenter)': 'showTooltip()',
    '(mouseleave)': 'hideTooltip()',
    '(focus)': 'showTooltip()',
    '(blur)': 'hideTooltip()',
    '(keydown.escape)': 'hideTooltip()',
    '(window:scroll)': 'refreshTooltip()',
  },
  template: `
    <app-interface-icon [name]="appRowAction()" />
    <span class="row-action-tooltip" aria-hidden="true" [hidden]="!anchor()" [class.below]="anchor()?.below"
      [style.left.px]="anchor()?.x" [style.top.px]="anchor()?.y">{{ label() }}</span>
  `,
})
export class RowAction {
  readonly appRowAction = input.required<RowActionKind>();
  readonly label = input.required<string>();
  protected readonly tones = TONES;
  protected readonly anchor = signal<{ x: number; y: number; below: boolean } | null>(null);
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef);

  protected showTooltip(): void {
    const { right, top, bottom } = this.element.nativeElement.getBoundingClientRect();
    const below = top < 48;
    const x = Math.max(right, Math.min(170, this.element.nativeElement.ownerDocument.documentElement.clientWidth));
    this.anchor.set({ x, y: below ? bottom + 7 : top - 7, below });
  }

  protected hideTooltip(): void {
    this.anchor.set(null);
  }

  protected refreshTooltip(): void {
    if (this.anchor()) this.showTooltip();
  }
}
