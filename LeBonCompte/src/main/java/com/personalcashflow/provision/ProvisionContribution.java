package com.personalcashflow.provision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One manually-entered addition toward a {@link Provision}'s target. Append-only: add or delete, never edit in place. */
@Entity
@Table(name = "provision_contribution")
public class ProvisionContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provision_id", nullable = false)
    private Provision provision;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    protected ProvisionContribution() {
    }

    public ProvisionContribution(Provision provision, BigDecimal amount, LocalDate date) {
        this.provision = provision;
        this.amount = amount;
        this.date = date;
    }

    public Long getId() {
        return id;
    }

    public Provision getProvision() {
        return provision;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }
}
