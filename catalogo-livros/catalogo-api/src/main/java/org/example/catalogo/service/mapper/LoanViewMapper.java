package org.example.catalogo.service.mapper;

import org.example.catalogo.model.FinePayment;
import org.example.catalogo.model.Loan;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.model.LoanPolicy;
import org.example.catalogo.service.dto.LoanContracts.LoanEventView;
import org.example.catalogo.service.dto.LoanContracts.LoanView;

import java.time.Instant;
import java.util.List;

public final class LoanViewMapper {

    private LoanViewMapper() {
    }

    public static LoanView forReader(Loan loan, Instant now, boolean hasPendingFine) {
        return toView(loan, true, now, hasPendingFine, false);
    }

    public static LoanView forDesk(Loan loan, Instant now) {
        return toView(loan, false, now, false, false);
    }

    public static LoanView forReaderHistory(Loan loan, Instant now, boolean hasPendingFine) {
        return toView(loan, true, now, hasPendingFine, true);
    }

    public static LoanView forDeskQueue(Loan loan, Instant now) {
        return toView(loan, false, now, false, true);
    }

    private static LoanView toView(Loan loan, boolean readerView, Instant now, boolean hasPendingFine, boolean showExpired) {
        List<FinePayment> payments = loan.getPayments();
        String lastOutcome = payments.isEmpty() ? null : payments.getLast().getOutcome().name();
        boolean pickupOpen = LoanPolicy.isPickupOpen(loan, now);
        return new LoanView(
            loan.getId(),
            loan.getBook().getId(),
            loan.getBook().getTitulo(),
            loan.getReader().getId(),
            loan.getReader().getNome(),
            loan.getReader().getEmail(),
            showExpired && loan.getStatus() == LoanStatus.RESERVED && !pickupOpen
                ? "EXPIRED" : loan.getStatus().name(),
            readerView && pickupOpen ? loan.getPickupCode() : null,
            loan.getRequestedAt(),
            loan.getPickupDeadline(),
            loan.getPickedUpAt(),
            loan.getDueAt(),
            loan.getReturnedAt(),
            loan.getRenewalCount(),
            LoanPolicy.MAX_RENEWALS,
            readerView && LoanPolicy.renewalBlockReason(loan, now, hasPendingFine) == null,
            readerView && pickupOpen,
            pickupOpen,
            loan.isOverdueAt(now),
            loan.getFineInCents(),
            loan.getFineStatus().name(),
            loan.getFinePaidAt(),
            lastOutcome,
            loan.getEvents().stream()
                .map(event -> new LoanEventView(
                    event.getType().name(),
                    event.getDescription(),
                    event.getActorName(),
                    event.getCreatedAt()
                ))
                .toList()
        );
    }
}
