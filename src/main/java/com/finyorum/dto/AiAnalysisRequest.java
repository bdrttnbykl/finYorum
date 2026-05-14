package com.finyorum.dto;

import jakarta.validation.constraints.NotBlank;

public record AiAnalysisRequest(@NotBlank String symbol) {
}
