package com.personalcashflow.auth.dto;

import com.personalcashflow.auth.AppUser;
import com.personalcashflow.auth.UserRole;
import java.time.Instant;

public record UserResponse(Long id, String username, UserRole role, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
    }
}
