package com.personalcashflow.provision.dto;

import com.personalcashflow.provision.ProvisionContribution;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProvisionContributionResponse(Long id, BigDecimal amount, LocalDate date) {

    public static ProvisionContributionResponse from(ProvisionContribution contribution) {
        return new ProvisionContributionResponse(contribution.getId(), contribution.getAmount(), contribution.getDate());
    }
}
