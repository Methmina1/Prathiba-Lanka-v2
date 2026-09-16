package com.example.PrathibaLanka.dto.response;

import com.example.PrathibaLanka.enums.ContentSection;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageContentResponseDTO {

    private ContentSection section;
    private JsonNode payload;
    private LocalDateTime updatedAt;
    private String updatedByName;
}
