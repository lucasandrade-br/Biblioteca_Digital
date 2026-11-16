package org.example.catalogo.model;

import org.example.catalogo.model.enums.LoanStatus;

import java.time.Duration;
import java.time.Instant;

public final class LoanPolicy {
    public static final Duration PICKUP_WINDOW = Duration.ofHours(48);
    public static final Duration LOAN_PERIOD = Duration.ofDays(14);
    public static final Duration RENEWAL_PERIOD = Duration.ofDays(14);
    public static final int MAX_RENEWALS = 2;

    private LoanPolicy() {
    }

    public static boolean isPickupOpen(Loan loan, Instant now) {
        return loan.getStatus() == LoanStatus.RESERVED && loan.getPickupDeadline().isAfter(now);
    }

    public static String renewalBlockReason(Loan loan, Instant now, boolean hasPendingFine) {
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            return "Somente empréstimos ativos podem ser renovados";
        }
        if (loan.isOverdueAt(now)) {
            return "Empréstimo em atraso não pode ser renovado. Devolva o livro no balcão";
        }
        if (hasPendingFine) {
            return "Você possui multa pendente. Regularize para renovar empréstimos";
        }
        if (loan.getRenewalCount() >= MAX_RENEWALS) {
            return "Limite de " + MAX_RENEWALS + " renovações atingido";
        }
        return null;
    }
}
