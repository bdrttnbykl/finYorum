package com.finyorum.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PortfolioAssetRequest(
        @NotNull Long userId,
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.000001") BigDecimal quantity,
        @NotNull @DecimalMin("0.000001") BigDecimal averagePrice
) {
}
