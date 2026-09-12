package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BookingRequestDTO {

    /** Optional; when sent it must match the authenticated customer, otherwise 403. */
    private Long customerId;

    @NotNull(message = "Package ID is required")
    private Long packageId;

    @NotNull(message = "Number of travelers is required")
    @Min(value = 1, message = "Number of travelers must be at least 1")
    private Integer numTravelers;

    @NotNull(message = "Preferred travel date is required")
    private LocalDate preferredTravelDate;

    private String specialRequests;
}