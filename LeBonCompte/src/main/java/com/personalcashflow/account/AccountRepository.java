package com.personalcashflow.account;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT COALESCE(SUM(a.initialBalance), 0) FROM Account a")
    BigDecimal sumInitialBalance();
}
