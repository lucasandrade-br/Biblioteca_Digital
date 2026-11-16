import { HttpErrorResponse } from '@angular/common/http';
import { TimeoutError } from 'rxjs';

interface ApiErrorBody {
  message?: string;
  errors?: Record<string, string>;
}

export function apiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof TimeoutError) return 'A conexão demorou mais que o esperado. Tente novamente.';
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0 || error.status === 502 || error.status === 504)
      return 'Não foi possível conectar ao serviço. Tente novamente em instantes.';
    const body = error.error as ApiErrorBody | null;
    const fieldErrors = body?.errors ? Object.values(body.errors) : [];
    if (fieldErrors.length) return fieldErrors.join(' · ');
    if (body?.message) return body.message;
  }
  return fallback;
}
