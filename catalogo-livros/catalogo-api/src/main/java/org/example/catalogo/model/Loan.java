package org.example.catalogo.model;

import org.example.catalogo.model.enums.FineStatus;
import org.example.catalogo.model.enums.LoanEventType;
import org.example.catalogo.model.enums.LoanStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reader_id", nullable = false)
    private Usuario reader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "pickup_code", nullable = false, unique = true, length = 12)
    private String pickupCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "pickup_deadline", nullable = false)
    private Instant pickupDeadline;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "renewal_count", nullable = false)
    private int renewalCount;

    @Column(name = "fine_in_cents", nullable = false)
    private long fineInCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "fine_status", nullable = false, length = 20)
    private FineStatus fineStatus;

    @Column(name = "fine_paid_at")
    private Instant finePaidAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LoanEvent> events = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FinePayment> payments = new ArrayList<>();

    protected Loan() {
    }

    public Loan(Usuario reader, Book book, String idempotencyKey, String pickupCode, Instant now, Instant pickupDeadline) {
        this.reader = reader;
        this.book = book;
        this.idempotencyKey = idempotencyKey;
        this.pickupCode = pickupCode;
        this.status = LoanStatus.RESERVED;
        this.requestedAt = now;
        this.pickupDeadline = pickupDeadline;
        this.fineStatus = FineStatus.NONE;
        this.updatedAt = now;
        addEvent(LoanEventType.RESERVED, "Reserva criada", reader.getNome(), now);
    }

    public static Loan lendAtDesk(Usuario reader, Book book, String key, Instant now, String staffName) {
        Loan loan = new Loan();
        loan.reader = reader;
        loan.book = book;
        loan.idempotencyKey = key;
        loan.status = LoanStatus.ACTIVE;
        loan.requestedAt = now;
        loan.pickedUpAt = now;
        loan.dueAt = now.plus(LoanPolicy.LOAN_PERIOD);
        loan.updatedAt = now;
        loan.fineStatus = FineStatus.NONE;
        loan.addEvent(LoanEventType.PICKED_UP, "Empréstimo presencial registrado no balcão", staffName, now);
        return loan;
    }

    public void pickUp(Instant now, Instant dueAt, String actorName) {
        this.status = LoanStatus.ACTIVE;
        this.pickedUpAt = now;
        this.dueAt = dueAt;
        this.updatedAt = now;
        addEvent(LoanEventType.PICKED_UP, "Livro retirado no balcão", actorName, now);
    }

    public void renew(Instant newDueAt, Instant now) {
        this.dueAt = newDueAt;
        this.renewalCount++;
        this.updatedAt = now;
        addEvent(LoanEventType.RENEWED, "Empréstimo renovado", reader.getNome(), now);
    }

    public void registerReturn(Instant now, long fineInCents, String actorName) {
        this.status = LoanStatus.RETURNED;
        this.returnedAt = now;
        this.updatedAt = now;
        addEvent(LoanEventType.RETURNED, "Livro devolvido no balcão", actorName, now);
        if (fineInCents > 0) {
            this.fineInCents = fineInCents;
            this.fineStatus = FineStatus.PENDING;
            addEvent(LoanEventType.FINE_CHARGED, "Multa por atraso gerada", actorName, now);
        }
    }

    public void cancel(Instant now) {
        this.status = LoanStatus.CANCELLED;
        this.updatedAt = now;
        addEvent(LoanEventType.CANCELLED, "Reserva cancelada pelo leitor", reader.getNome(), now);
    }

    public void expire(Instant now) {
        this.status = LoanStatus.EXPIRED;
        this.updatedAt = now;
        addEvent(LoanEventType.EXPIRED, "Prazo de retirada expirado", "Sistema", now);
    }

    public void addPayment(FinePayment payment) {
        payments.add(payment);
    }

    public void markFinePaid(Instant now) {
        this.fineStatus = FineStatus.PAID;
        this.finePaidAt = now;
        this.updatedAt = now;
        addEvent(LoanEventType.FINE_PAID, "Multa paga (pagamento simulado)", reader.getNome(), now);
    }

    public void registerDeclinedPayment(Instant now) {
        this.updatedAt = now;
        addEvent(LoanEventType.FINE_DECLINED, "Pagamento simulado da multa recusado", reader.getNome(), now);
    }

    public boolean isOverdueAt(Instant now) {
        return status == LoanStatus.ACTIVE && dueAt.isBefore(now);
    }

    private void addEvent(LoanEventType type, String description, String actorName, Instant now) {
        events.add(new LoanEvent(this, type, description, actorName, now));
    }

    public Long getId() {
        return id;
    }

    public Usuario getReader() {
        return reader;
    }

    public Book getBook() {
        return book;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPickupCode() {
        return pickupCode;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getPickupDeadline() {
        return pickupDeadline;
    }

    public Instant getPickedUpAt() {
        return pickedUpAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public int getRenewalCount() {
        return renewalCount;
    }

    public long getFineInCents() {
        return fineInCents;
    }

    public FineStatus getFineStatus() {
        return fineStatus;
    }

    public Instant getFinePaidAt() {
        return finePaidAt;
    }

    public List<LoanEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<FinePayment> getPayments() {
        return Collections.unmodifiableList(payments);
    }
}
