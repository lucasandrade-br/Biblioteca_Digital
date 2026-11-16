import { ValidatorFn } from '@angular/forms';

export const notBlank: ValidatorFn = (control) =>
  typeof control.value === 'string' && control.value.trim().length ? null : { required: true };
