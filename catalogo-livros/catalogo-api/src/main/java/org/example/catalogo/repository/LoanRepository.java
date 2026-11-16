package org.example.catalogo.repository;

import org.example.catalogo.model.Loan;
import org.example.catalogo.model.enums.FineStatus;
import org.example.catalogo.model.enums.LoanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    Optional<Loan> findByReader_IdAndIdempotencyKey(Long readerId, String idempotencyKey);

    Optional<Loan> findByIdAndReader_Id(Long id, Long readerId);

    Optional<Loan> findFirstByReader_IdAndBook_IdAndStatusAndPickupDeadlineAfterOrderById(
        Long readerId, Long bookId, LoanStatus status, Instant now
    );

    Optional<Loan> findByPickupCode(String pickupCode);

    @Query("""
        SELECT l FROM Loan l
        WHERE l.reader.id = :readerId
          AND (:scope <> 'RESERVAS' OR (l.status = 'RESERVED' AND l.pickupDeadline > :now))
          AND (:scope <> 'EMPRESTIMOS' OR l.status = 'ACTIVE')
          AND (:scope <> 'HISTORICO' OR (l.status IN ('RETURNED', 'CANCELLED', 'EXPIRED')
               OR (l.status = 'RESERVED' AND l.pickupDeadline <= :now)))
          AND (:status IS NULL OR l.status = :status
               OR (:scope = 'HISTORICO' AND :status = 'EXPIRED' AND l.status = 'RESERVED' AND l.pickupDeadline <= :now))
          AND (:term = '' OR LOCATE(:term, LOWER(l.book.titulo)) > 0)
        """)
    Page<Loan> searchMine(
        @Param("readerId") Long readerId, @Param("status") LoanStatus status,
        @Param("term") String term, @Param("scope") String scope, @Param("now") Instant now,
        Pageable pageable
    );

    @Query("""
        SELECT l FROM Loan l
        WHERE (:status IS NULL OR l.status = :status
               OR (:status = 'EXPIRED' AND l.status = 'RESERVED' AND l.pickupDeadline <= :now))
          AND (:status IS NULL OR :status <> 'RESERVED' OR l.pickupDeadline > :now)
          AND (:pending = FALSE OR l.status = 'ACTIVE' OR (l.status = 'RESERVED' AND l.pickupDeadline > :now))
          AND (:overdue = FALSE OR (l.status = 'ACTIVE' AND l.dueAt < :now))
          AND (:fromDate IS NULL AND :untilDate IS NULL
               OR (l.status = 'ACTIVE' AND (:fromDate IS NULL OR l.dueAt >= :fromDate)
                   AND (:untilDate IS NULL OR l.dueAt < :untilDate))
               OR (l.status = 'RESERVED' AND l.pickupDeadline > :now
                   AND (:fromDate IS NULL OR l.pickupDeadline >= :fromDate)
                   AND (:untilDate IS NULL OR l.pickupDeadline < :untilDate)))
          AND (:term = '' OR LOCATE(:term, LOWER(l.book.titulo)) > 0
            OR LOCATE(:term, LOWER(l.reader.nome)) > 0 OR LOCATE(:term, LOWER(l.reader.email)) > 0)
        ORDER BY
          CASE WHEN :pending = TRUE AND l.status = 'ACTIVE' AND l.dueAt < :now THEN 0 ELSE 1 END,
          CASE WHEN :pending = TRUE AND l.status = 'ACTIVE' THEN l.dueAt
               WHEN :pending = TRUE AND l.status = 'RESERVED' THEN l.pickupDeadline ELSE NULL END
        """)
    Page<Loan> searchDesk(
        @Param("status") LoanStatus status, @Param("term") String term,
        @Param("pending") boolean pending, @Param("overdue") boolean overdue,
        @Param("fromDate") Instant fromDate, @Param("untilDate") Instant untilDate,
        @Param("now") Instant now, Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Loan> findWithLockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Loan> findWithLockByIdAndReader_Id(Long id, Long readerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Loan> findWithLockByPickupCode(String pickupCode);

    @Query("SELECT l.id FROM Loan l WHERE l.status = :status AND l.pickupDeadline <= :now ORDER BY l.id")
    List<Long> findDueReservationIds(@Param("status") LoanStatus status, @Param("now") Instant now, Pageable page);

    @Query("""
        SELECT l.id FROM Loan l
        WHERE l.status = :status AND l.pickupDeadline <= :now
          AND (l.book.id = :bookId OR l.reader.id = :readerId)
        ORDER BY l.id
        """)
    List<Long> findDueReservationIdsForReservation(
        @Param("status") LoanStatus status, @Param("now") Instant now,
        @Param("bookId") Long bookId, @Param("readerId") Long readerId
    );

    long countByReader_IdAndStatusIn(Long readerId, Collection<LoanStatus> statuses);

    boolean existsByReader_IdAndBook_IdAndStatusIn(Long readerId, Long bookId, Collection<LoanStatus> statuses);

    boolean existsByReader_IdAndStatusAndDueAtBefore(Long readerId, LoanStatus status, Instant now);

    boolean existsByReader_IdAndFineStatus(Long readerId, FineStatus fineStatus);

    boolean existsByBook_IdAndStatusIn(Long bookId, Collection<LoanStatus> statuses);

    boolean existsByReader_IdAndStatusIn(Long readerId, Collection<LoanStatus> statuses);

    long countByStatus(LoanStatus status);

    long countByStatusAndDueAtBefore(LoanStatus status, Instant now);

    @Query("SELECT COALESCE(SUM(l.fineInCents), 0) FROM Loan l WHERE l.fineStatus = :fineStatus")
    long sumFineByStatus(@Param("fineStatus") FineStatus fineStatus);
}
