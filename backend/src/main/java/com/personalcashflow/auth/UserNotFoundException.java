package com.personalcashflow.auth;

import com.personalcashflow.common.NotFoundException;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(Long id) {
        super("User not found: " + id);
    }
}
