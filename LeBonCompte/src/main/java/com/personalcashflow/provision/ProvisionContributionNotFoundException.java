package com.personalcashflow.provision;

import com.personalcashflow.common.NotFoundException;

public class ProvisionContributionNotFoundException extends NotFoundException {

    public ProvisionContributionNotFoundException(Long id) {
        super("Provision contribution not found: " + id);
    }
}
