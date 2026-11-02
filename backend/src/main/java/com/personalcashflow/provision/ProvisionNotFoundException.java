package com.personalcashflow.provision;

import com.personalcashflow.common.NotFoundException;

public class ProvisionNotFoundException extends NotFoundException {

    public ProvisionNotFoundException(Long id) {
        super("Provision not found: " + id);
    }
}
