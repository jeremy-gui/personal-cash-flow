package com.personalcashflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsernameRequest(@NotBlank @Size(max = 50) String username) {
}
