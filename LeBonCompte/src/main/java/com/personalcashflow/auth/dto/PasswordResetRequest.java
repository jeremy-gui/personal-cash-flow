package com.personalcashflow.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(@NotNull @Size(min = 8, max = 72) String password) {
}
