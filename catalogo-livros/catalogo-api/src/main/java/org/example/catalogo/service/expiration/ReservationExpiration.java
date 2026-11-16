package org.example.catalogo.service.expiration;

import org.example.catalogo.model.LoanPolicy;
import org.example.catalogo.model.enums.LoanStatus;
import org.example.catalogo.repository.BookRepository;
import org.example.catalogo.repository.LoanRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

@Service
public class ReservationExpiration {
    private final LoanRepository loans;
    private final BookRepository books;
    private final TransactionTemplate transaction;

    public ReservationExpiration(LoanRepository loans, BookRepository books, PlatformTransactionManager manager) {
        this.loans = loans;
        this.books = books;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${catalogo.loans.expiration-scan-ms:60000}")
    public void expireScheduledLoans() {
        Instant now = Instant.now();
        expire(loans.findDueReservationIds(LoanStatus.RESERVED, now, PageRequest.of(0, 100)), now);
    }

    public void expireForReservation(Long bookId, Long readerId, Instant now) {
        expire(loans.findDueReservationIdsForReservation(LoanStatus.RESERVED, now, bookId, readerId), now);
    }

    private void expire(List<Long> ids, Instant now) {
        for (Long id : ids) {
            transaction.executeWithoutResult(status -> {
                var loan = loans.findWithLockById(id).orElse(null);
                if (loan == null || loan.getStatus() != LoanStatus.RESERVED || LoanPolicy.isPickupOpen(loan, now)) {
                    return;
                }
                var book = books.findWithLockById(loan.getBook().getId())
                    .orElseThrow(() -> new IllegalStateException("Livro do empréstimo não encontrado"));
                loan.expire(now);
                book.releaseCopy();
            });
        }
    }
}
