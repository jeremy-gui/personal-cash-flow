import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Form-level check that the two password fields hold the same value. */
export function passwordsMatch(field: string, confirmField: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null =>
    group.get(field)?.value === group.get(confirmField)?.value ? null : { passwordsMismatch: true };
}
