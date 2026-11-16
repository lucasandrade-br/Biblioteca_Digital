package org.example.catalogo.model;

import org.example.catalogo.model.enums.LoanEventType;
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
@Table(name = "loan_events")
public class LoanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LoanEventType type;

    @Column(nullable = false)
    private String description;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LoanEvent() {
    }

    LoanEvent(Loan loan, LoanEventType type, String description, String actorName, Instant createdAt) {
        this.loan = loan;
        this.type = type;
        this.description = description;
        this.actorName = actorName;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LoanEventType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getActorName() {
        return actorName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
