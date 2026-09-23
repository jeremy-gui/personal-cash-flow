package com.personalcashflow.transaction;

import java.math.BigDecimal;

public interface MonthlyCategorySpendingProjection {
    Long getCategoryId();

    String getCategoryName();

    Integer getMonth();

    BigDecimal getTotal();
}
