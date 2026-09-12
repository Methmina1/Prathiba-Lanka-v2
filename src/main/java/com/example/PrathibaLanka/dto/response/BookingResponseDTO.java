package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.BookingStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BookingResponseDTO {
    private Long bookingId;
    private String pinCode;
    private BookingStatus status;
    private String customerName;
    private String customerEmail;
    private String packageTitle;
    private String destination;
    private Integer numTravelers;
    private LocalDate preferredTravelDate;
    private String specialRequests;
    private BigDecimal confirmedPrice;
    private LocalDate confirmedDate;
}