import { Component, Input, OnChanges, signal } from '@angular/core';
import * as QRCode from 'qrcode';

@Component({
  selector: 'app-pickup-qr',
  template: `
    @if (imageUrl()) {
      <img class="pickup-qr" [src]="imageUrl()" [alt]="'QR de retirada de ' + label" />
    } @else if (failed()) {
      <span role="alert">Não foi possível gerar o QR. Use o código abaixo.</span>
    } @else {
      <span role="status">Preparando QR...</span>
    }
  `,
})
export class PickupQr implements OnChanges {
  @Input({ required: true }) value = '';
  @Input({ required: true }) label = '';

  protected readonly imageUrl = signal('');
  protected readonly failed = signal(false);

  ngOnChanges(): void {
    const value = this.value;
    this.imageUrl.set('');
    this.failed.set(false);
    void QRCode.toString(value, { errorCorrectionLevel: 'M', margin: 2, type: 'svg' })
      .then((svg) => {
        if (this.value === value) {
          this.imageUrl.set(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`);
        }
      })
      .catch(() => {
        if (this.value === value) this.failed.set(true);
      });
  }
}
