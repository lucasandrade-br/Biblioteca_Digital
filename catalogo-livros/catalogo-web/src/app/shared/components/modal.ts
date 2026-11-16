import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  input,
  output,
  viewChild,
} from '@angular/core';

@Component({
  selector: 'app-modal',
  template: `
    <dialog #surface class="dialog-surface" [class.dialog-wide]="wide()" aria-labelledby="dialog-heading"
      [attr.closedby]="pending() ? 'none' : 'closerequest'"
      (cancel)="$event.preventDefault(); requestClose()">
      <div class="dialog-header">
        <h2 #heading id="dialog-heading" tabindex="-1">{{ title() }}</h2>
        <button type="button" class="dialog-close" aria-label="Fechar" [disabled]="pending()"
          (click)="requestClose()">
          <span aria-hidden="true">×</span>
        </button>
      </div>
      <div class="dialog-body"><ng-content /></div>
    </dialog>
  `,
})
export class Modal implements AfterViewInit, OnDestroy {
  readonly title = input.required<string>();
  readonly pending = input(false);
  readonly wide = input(false);
  readonly dismissed = output<void>();
  private readonly surface = viewChild.required<ElementRef<HTMLDialogElement>>('surface');
  private readonly heading = viewChild.required<ElementRef<HTMLElement>>('heading');
  private dialog?: HTMLDialogElement;
  private opener?: HTMLElement;
  private page?: HTMLElement;

  ngAfterViewInit(): void {
    const dialog = this.surface().nativeElement;
    const active = dialog.ownerDocument.activeElement;
    this.page = dialog.closest('main') ?? undefined;
    if (active instanceof HTMLElement && active !== this.page && this.page?.contains(active)) {
      this.opener = active;
    }
    this.dialog = dialog;
    dialog.showModal();
    (dialog.querySelector<HTMLElement>('[data-dialog-initial-focus]') ??
      this.heading().nativeElement).focus();
  }

  protected requestClose(): void {
    if (!this.pending()) this.dismissed.emit();
  }

  ngOnDestroy(): void {
    const dialog = this.dialog;
    if (!dialog) return;

    dialog.close();
    queueMicrotask(() => {
      if (dialog.ownerDocument.querySelector('dialog[open]')) return;
      const active = dialog.ownerDocument.activeElement;
      if (active && active !== dialog.ownerDocument.body && !dialog.contains(active)) return;
      if (this.opener?.isConnected) this.opener.focus({ preventScroll: true });
      else this.page?.querySelector<HTMLElement>('h1[tabindex="-1"]')?.focus({ preventScroll: true });
    });
  }
}
