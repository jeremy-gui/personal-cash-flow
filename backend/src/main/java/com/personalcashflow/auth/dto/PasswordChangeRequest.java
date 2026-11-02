package com.personalcashflow.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotNull String currentPassword,
        @NotNull @Size(min = 8, max = 72) String newPassword) {
}
