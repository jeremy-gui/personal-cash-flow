package com.personalcashflow.repartition.dto;

import java.math.BigDecimal;

/** Average monthly net frais over the current year's complete months ({@code from}..{@code to}, yyyy-MM). */
public record AverageFraisResponse(int months, String from, String to, BigDecimal average) {
}
