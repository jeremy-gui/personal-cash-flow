package com.personalcashflow.imports.dto;

import com.personalcashflow.imports.ImportBatch;
import java.time.Instant;

public record ImportSummaryResponse(
        Long id,
        Long accountId,
        String filename,
        Instant importedAt,
        int rowCount,
        int createdCount,
        int skippedCount) {

    public static ImportSummaryResponse from(ImportBatch batch) {
        return new ImportSummaryResponse(
                batch.getId(),
                batch.getAccount().getId(),
                batch.getFilename(),
                batch.getImportedAt(),
                batch.getRowCount(),
                batch.getCreatedCount(),
                batch.getSkippedCount());
    }
}
