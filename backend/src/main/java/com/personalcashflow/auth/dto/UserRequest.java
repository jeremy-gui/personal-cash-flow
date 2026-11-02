package com.personalcashflow.auth.dto;

import com.personalcashflow.auth.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank @Size(max = 50) String username,
        @NotNull @Size(min = 8, max = 72) String password,
        @NotNull UserRole role) {
}
