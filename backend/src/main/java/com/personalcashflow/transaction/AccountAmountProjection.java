package com.personalcashflow.transaction;

import java.math.BigDecimal;

public interface AccountAmountProjection {
    Long getAccountId();

    String getAccountName();

    BigDecimal getTotal();
}
