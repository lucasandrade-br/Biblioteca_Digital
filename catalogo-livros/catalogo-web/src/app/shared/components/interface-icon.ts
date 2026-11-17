import { Component, input } from '@angular/core';

const PATHS = {
  edit: 'M12 20h9M4 16l-1 5 5-1L20 8a2.8 2.8 0 0 0-4-4L4 16Zm10-10 4 4',
  delete: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7',
  deactivate: 'M12 8v4m0 4h.01M5.6 5.6a9 9 0 1 0 12.8 12.8A9 9 0 0 0 5.6 5.6ZM5.6 5.6l12.8 12.8',
  activate: 'M4 12a8 8 0 1 0 3-6m-3-2v5h5m0 4 2 2 4-4',
  grant: 'M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m9-14a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm7 1v6m-3-3h6',
  revoke: 'M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m9-14a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm4 4h6',
  return: 'M9 10 4 15l5 5M4 15h11a5 5 0 0 0 0-10h-3',
  pickup: 'M14 9l5 5-5 5m5-5h-7M9 3H5v18h4',
  books: 'M12 6v15M12 6C9 3 5 3 2 4v15c3-1 7-1 10 2 3-3 7-3 10-2V4c-3-1-7-1-10 2Z',
  operations: 'M3 9h18v11H3zM7 9V5h10v4M3 13h18m-11 0v2h4v-2',
  system: 'M4 6h16M4 12h16M4 18h16M8 4v4m8 2v4m-7 2v4',
  menu: 'M4 6h16M4 12h16M4 18h16',
} as const;

export type IconName = keyof typeof PATHS;

@Component({
  selector: 'app-interface-icon',
  template: `
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
      stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      <path [attr.d]="paths[name()]" />
    </svg>
  `,
  styles: ':host, svg { display: block; }',
})
export class InterfaceIcon {
  readonly name = input.required<IconName>();
  protected readonly paths = PATHS;
}
