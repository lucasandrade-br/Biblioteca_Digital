import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export const passwordsMatch: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.get('password')?.value === control.get('confirmation')?.value
    ? null
    : { passwordsMismatch: true };
