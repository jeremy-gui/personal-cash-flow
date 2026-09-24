package com.personalcashflow.repartition;

import com.personalcashflow.common.NotFoundException;

public class RepartitionLineNotFoundException extends NotFoundException {

    public RepartitionLineNotFoundException(Long id) {
        super("Repartition line not found: " + id);
    }
}
