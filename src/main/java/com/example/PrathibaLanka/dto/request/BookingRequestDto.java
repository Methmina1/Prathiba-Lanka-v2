package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BookingRequestDto {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Package ID is required")
    private Long packageId;

    @Min(value = 1, message = "Number of travelers must be at least 1")
    private int travelers;

    @NotNull(message = "Preferred travel date is required")
    private LocalDate preferredTravelDate;

    private String specialRequests;
}