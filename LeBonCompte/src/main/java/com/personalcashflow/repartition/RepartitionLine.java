package com.personalcashflow.repartition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * A hand-entered input of the salary split ("calcul répartition"): each person's salary, plus the
 * frais/aides that aren't recurring transactions (e.g. "alimentation", "renflouage"). Recurring
 * transactions are read live, never copied here.
 */
@Entity
@Table(name = "repartition_line")
public class RepartitionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepartitionLineKind kind;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private BigDecimal amount;

    protected RepartitionLine() {
    }

    public RepartitionLine(RepartitionLineKind kind, String label, BigDecimal amount) {
        this.kind = kind;
        this.label = label;
        this.amount = amount;
    }

    public void update(RepartitionLineKind kind, String label, BigDecimal amount) {
        this.kind = kind;
        this.label = label;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public RepartitionLineKind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
