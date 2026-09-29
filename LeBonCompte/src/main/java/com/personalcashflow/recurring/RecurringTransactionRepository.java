package com.personalcashflow.recurring;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    /**
     * Takes a Postgres advisory lock held until the current transaction ends, so concurrent
     * generations (a page fires several requests at once, each generating its month) run one after
     * the other instead of all seeing "not generated yet" and inserting the same instance.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(4242)) AS generation_lock", nativeQuery = true)
    Integer lockGeneration();
}
