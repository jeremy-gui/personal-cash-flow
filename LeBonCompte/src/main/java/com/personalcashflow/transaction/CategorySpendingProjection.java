package com.personalcashflow.transaction;

import java.math.BigDecimal;

public interface CategorySpendingProjection {
    Long getCategoryId();

    String getCategoryName();

    BigDecimal getTotal();
}
