package com.personalcashflow.repartition.dto;

import com.personalcashflow.repartition.RepartitionLine;
import com.personalcashflow.repartition.RepartitionLineKind;
import java.math.BigDecimal;

public record RepartitionLineResponse(Long id, RepartitionLineKind kind, String label, BigDecimal amount) {

    public static RepartitionLineResponse of(RepartitionLine line) {
        return new RepartitionLineResponse(line.getId(), line.getKind(), line.getLabel(), line.getAmount());
    }
}
