package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BookingConfirmRequestDTO {
    @NotNull(message = "Confirmed price is required")
    @Positive(message = "Confirmed price must be positive")
    private BigDecimal confirmedPrice;

    @NotNull(message = "Confirmed date is required")
    private LocalDate confirmedDate;
}