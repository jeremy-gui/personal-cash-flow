package com.personalcashflow.recurring;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringTransactionSkipRepository extends JpaRepository<RecurringTransactionSkip, Long> {

    boolean existsByRecurringTransactionIdAndMonth(Long recurringTransactionId, LocalDate month);
}
