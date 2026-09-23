package com.personalcashflow.provision.dto;

import com.personalcashflow.provision.Provision;
import com.personalcashflow.provision.ProvisionContribution;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record ProvisionResponse(
        Long id,
        String name,
        BigDecimal targetAmount,
        LocalDate targetDate,
        Long categoryId,
        String categoryName,
        Long accountId,
        String accountName,
        boolean active,
        BigDecimal currentAmount,
        BigDecimal remaining,
        BigDecimal percentageComplete,
        BigDecimal monthlyEffortNeeded,
        boolean atRisk) {

    /**
     * {@code currentAmount} is never stored: it's the live sum of {@code contributions}, the same
     * "compute from the source of truth, don't store a derived running total" rule {@code
     * Budget.spent} already follows.
     */
    public static ProvisionResponse of(Provision provision, List<ProvisionContribution> contributions) {
        BigDecimal currentAmount =
                contributions.stream().map(ProvisionContribution::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = provision.getTargetAmount().subtract(currentAmount);
        BigDecimal percentageComplete = provision.getTargetAmount().signum() == 0
                ? BigDecimal.ZERO
                : currentAmount
                        .multiply(BigDecimal.valueOf(100))
                        .divide(provision.getTargetAmount(), 1, RoundingMode.HALF_UP);

        Long monthsUntilTarget = provision.getTargetDate() == null
                ? null
                : ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(provision.getTargetDate()));

        // Only a meaningful "spread the rest out" figure when there's still time and still
        // something left to provision - overdue or already-met targets fall through to null,
        // with `atRisk` carrying the "overdue and short" signal instead.
        BigDecimal monthlyEffortNeeded =
                monthsUntilTarget != null && monthsUntilTarget > 0 && remaining.signum() > 0
                        ? remaining.divide(BigDecimal.valueOf(monthsUntilTarget), 2, RoundingMode.HALF_UP)
                        : null;

        boolean atRisk = provision.getTargetDate() != null
                && remaining.signum() > 0
                && (monthsUntilTarget == null || monthsUntilTarget <= 0);

        var category = provision.getCategory();
        var account = provision.getAccount();
        return new ProvisionResponse(
                provision.getId(),
                provision.getName(),
                provision.getTargetAmount(),
                provision.getTargetDate(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                account != null ? account.getId() : null,
                account != null ? account.getName() : null,
                provision.isActive(),
                currentAmount,
                remaining,
                percentageComplete,
                monthlyEffortNeeded,
                atRisk);
    }
}
