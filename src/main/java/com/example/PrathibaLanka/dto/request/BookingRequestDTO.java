package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * Who is asking, and where a reply should go.
     *
     * <p>Required for a request sent from the public form, which is how most of them arrive: the
     * person browsing the site has no account. When the request carries a customer token these are
     * filled in from the account instead and whatever the body says is ignored, so a signed-in
     * customer cannot make a booking look like it came from somebody else. The service decides which,
     * because "required unless authenticated" is not something a field annotation can express.
     */
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String contactName;

    @Email(message = "Email must be well-formed")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String contactEmail;
}
