package org.example.catalogo.controller;

import org.example.catalogo.dto.LoanResponse;
import org.example.catalogo.dto.LoanSummaryResponse;
import org.example.catalogo.model.enums.FineStatus;
import org.example.catalogo.model.enums.LoanEventType;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.model.enums.PaymentOutcome;
import org.example.catalogo.service.dto.LoanContracts.LoanSummaryView;
import org.example.catalogo.service.dto.LoanContracts.LoanView;

final class LoanHttpMapper {

    private LoanHttpMapper() {
    }

    static LoanResponse toResponse(LoanView loan) {
        return new LoanResponse(
            loan.id(), loan.bookId(), loan.bookTitle(), loan.readerId(), loan.readerName(), loan.readerEmail(),
            LoanStatus.valueOf(loan.status()), loan.pickupCode(), loan.requestedAt(), loan.pickupDeadline(),
            loan.pickedUpAt(), loan.dueAt(), loan.returnedAt(), loan.renewalCount(), loan.maxRenewals(),
            loan.canRenew(), loan.canCancel(), loan.canPickup(), loan.overdue(),
            loan.fineInCents(), FineStatus.valueOf(loan.fineStatus()), loan.finePaidAt(),
            loan.lastPaymentOutcome() == null ? null : PaymentOutcome.valueOf(loan.lastPaymentOutcome()),
            loan.events().stream()
                .map(event -> new LoanResponse.Event(
                    LoanEventType.valueOf(event.type()), event.description(), event.actorName(), event.createdAt()
                ))
                .toList()
        );
    }

    static LoanSummaryResponse toResponse(LoanSummaryView summary) {
        return new LoanSummaryResponse(
            summary.reservedCount(), summary.activeCount(), summary.overdueCount(),
            summary.pendingFinesInCents(), summary.paidFinesInCents()
        );
    }
}
