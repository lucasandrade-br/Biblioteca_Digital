import { Component } from '@angular/core';
import { CatalogRecords } from './catalog-records';

@Component({
  imports: [CatalogRecords],
  template: `<app-catalog-records kind="author" />`,
})
export class Authors {}
