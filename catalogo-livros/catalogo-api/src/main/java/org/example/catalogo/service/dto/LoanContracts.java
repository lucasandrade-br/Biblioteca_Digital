package org.example.catalogo.service.dto;

import java.time.Instant;
import java.util.List;

public final class LoanContracts {

    private LoanContracts() {
    }

    public record LoanView(
        Long id,
        Long bookId,
        String bookTitle,
        Long readerId,
        String readerName,
        String readerEmail,
        String status,
        String pickupCode,
        Instant requestedAt,
        Instant pickupDeadline,
        Instant pickedUpAt,
        Instant dueAt,
        Instant returnedAt,
        int renewalCount,
        int maxRenewals,
        boolean canRenew,
        boolean canCancel,
        boolean canPickup,
        boolean overdue,
        long fineInCents,
        String fineStatus,
        Instant finePaidAt,
        String lastPaymentOutcome,
        List<LoanEventView> events
    ) {
        public LoanView {
            events = List.copyOf(events);
        }
    }

    public record LoanEventView(String type, String description, String actorName, Instant createdAt) {
    }

    public record LoanSummaryView(
        long reservedCount,
        long activeCount,
        long overdueCount,
        long pendingFinesInCents,
        long paidFinesInCents
    ) {
    }

    public record PaymentRequest(Long loanId, long amountInCents) {
    }

    public enum PaymentResult {
        APPROVED,
        DECLINED
    }
}
