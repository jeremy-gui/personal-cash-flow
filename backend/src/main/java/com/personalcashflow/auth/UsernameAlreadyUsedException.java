package com.personalcashflow.auth;

import com.personalcashflow.common.ConflictException;

public class UsernameAlreadyUsedException extends ConflictException {

    public UsernameAlreadyUsedException(String username) {
        super("Cet identifiant est déjà utilisé : " + username);
    }
}
