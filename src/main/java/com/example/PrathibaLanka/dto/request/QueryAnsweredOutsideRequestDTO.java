package com.example.PrathibaLanka.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Recording that the agency answered from its own inbox.
 *
 * <p>The note is optional on purpose: a reply sent from a phone is worth marking as answered even when
 * nobody kept a copy of the words, and demanding one would push people into inventing them.
 */
@Data
public class QueryAnsweredOutsideRequestDTO {

    @Size(max = 5000, message = "Please keep the note under 5000 characters")
    private String note;
}
