package com.personalcashflow.common;

import java.util.Map;

public record ApiError(String message, Map<String, String> fieldErrors) {

    public static ApiError of(String message) {
        return new ApiError(message, Map.of());
    }

    public static ApiError of(String message, Map<String, String> fieldErrors) {
        return new ApiError(message, fieldErrors);
    }
}
