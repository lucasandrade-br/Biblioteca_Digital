package org.example.catalogo.model;

import org.example.catalogo.model.enums.PaymentOutcome;
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
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "fine_payments")
public class FinePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "attempt_key", nullable = false, unique = true, length = 64)
    private String attemptKey;

    @Column(nullable = false, length = 20)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOutcome outcome;

    @Column(name = "amount_in_cents", nullable = false)
    private long amountInCents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FinePayment() {
    }

    public FinePayment(Loan loan, String attemptKey, String provider, PaymentOutcome outcome, long amountInCents, Instant createdAt) {
        this.loan = loan;
        this.attemptKey = attemptKey;
        this.provider = provider;
        this.outcome = outcome;
        this.amountInCents = amountInCents;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getAttemptKey() {
        return attemptKey;
    }

    public String getProvider() {
        return provider;
    }

    public PaymentOutcome getOutcome() {
        return outcome;
    }

    public long getAmountInCents() {
        return amountInCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
