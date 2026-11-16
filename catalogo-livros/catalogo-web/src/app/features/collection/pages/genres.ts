import { Component } from '@angular/core';
import { CatalogRecords } from './catalog-records';

@Component({
  imports: [CatalogRecords],
  template: `<app-catalog-records kind="genre" />`,
})
export class Genres {}
