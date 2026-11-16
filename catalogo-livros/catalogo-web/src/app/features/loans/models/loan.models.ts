export type LoanStatus = 'RESERVED' | 'ACTIVE' | 'RETURNED' | 'CANCELLED' | 'EXPIRED';
export type LoanScope = 'RESERVAS' | 'EMPRESTIMOS' | 'HISTORICO';

export interface LoanFilter {
  q: string;
  status: LoanStatus | null;
}

export type FineStatus = 'NONE' | 'PENDING' | 'PAID';

export interface LoanEvent {
  type: string;
  description: string;
  actorName: string;
  createdAt: string;
}

export interface Loan {
  id: number;
  bookId: number;
  bookTitle: string;
  readerId: number;
  readerName: string;
  readerEmail: string;
  status: LoanStatus;
  pickupCode: string | null;
  requestedAt: string;
  pickupDeadline: string | null;
  pickedUpAt: string | null;
  dueAt: string | null;
  returnedAt: string | null;
  renewalCount: number;
  maxRenewals: number;
  canRenew: boolean;
  canCancel: boolean;
  canPickup: boolean;
  overdue: boolean;
  fineInCents: number;
  fineStatus: FineStatus;
  finePaidAt: string | null;
  lastPaymentOutcome: 'APPROVED' | 'DECLINED' | null;
  events: LoanEvent[];
}

export const LOAN_STATUS_LABELS: Record<LoanStatus, string> = {
  RESERVED: 'Reservado',
  ACTIVE: 'Emprestado',
  RETURNED: 'Devolvido',
  CANCELLED: 'Cancelado',
  EXPIRED: 'Expirado',
};
