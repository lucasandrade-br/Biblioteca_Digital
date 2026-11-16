package org.example.catalogo.dto;

import org.example.catalogo.model.enums.FineStatus;
import org.example.catalogo.model.enums.LoanEventType;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.model.enums.PaymentOutcome;

import java.time.Instant;
import java.util.List;

public record LoanResponse(
    Long id,
    Long bookId,
    String bookTitle,
    Long readerId,
    String readerName,
    String readerEmail,
    LoanStatus status,
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
    FineStatus fineStatus,
    Instant finePaidAt,
    PaymentOutcome lastPaymentOutcome,
    List<Event> events
) {
    public record Event(LoanEventType type, String description, String actorName, Instant createdAt) {
    }
}
