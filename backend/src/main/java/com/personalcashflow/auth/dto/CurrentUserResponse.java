package com.personalcashflow.auth.dto;

import com.personalcashflow.auth.AppUser;
import com.personalcashflow.auth.UserRole;

public record CurrentUserResponse(String username, UserRole role) {

    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(user.getUsername(), user.getRole());
    }
}
