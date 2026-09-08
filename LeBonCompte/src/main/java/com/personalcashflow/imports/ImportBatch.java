package com.personalcashflow.imports;

import com.personalcashflow.account.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private String filename;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "created_count", nullable = false)
    private int createdCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    protected ImportBatch() {
    }

    public ImportBatch(Account account, String filename, int rowCount, int createdCount, int skippedCount) {
        this.account = account;
        this.filename = filename;
        this.rowCount = rowCount;
        this.createdCount = createdCount;
        this.skippedCount = skippedCount;
        this.importedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getFilename() {
        return filename;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
}
