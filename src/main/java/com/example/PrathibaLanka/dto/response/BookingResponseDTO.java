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
    private BigDecimal confirmedPrice;
    private LocalDate confirmedDate;

    private String customerName;
    private String packageTitle;
    private int numTravelers;
    private LocalDate preferredTravelDate;
}