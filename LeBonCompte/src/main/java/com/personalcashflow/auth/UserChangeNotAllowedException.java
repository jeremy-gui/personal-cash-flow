package com.personalcashflow.auth;

import com.personalcashflow.common.BadRequestException;

public class UserChangeNotAllowedException extends BadRequestException {

    public UserChangeNotAllowedException(String message) {
        super(message);
    }
}
