package com.example.PrathibaLanka.dto.request;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PageContentRequestDTO {

    /** The whole section as a JSON object. Its shape is checked per section by the service. */
    @NotNull(message = "payload is required")
    private JsonNode payload;
}
