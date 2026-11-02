package com.personalcashflow.transaction;

public interface LabelCategoryProjection {
    String getOriginalLabel();

    String getLabel();

    Long getCategoryId();
}
