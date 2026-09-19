package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.PageContent;
import com.example.PrathibaLanka.enums.ContentSection;
import com.example.PrathibaLanka.exception.BadRequestException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.PageContentRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the editable copy of the About and Contact pages.
 *
 * <p>The payload is stored as JSON, but it is not stored blindly: every section has a required
 * shape and a length budget, so a bad save is rejected with a message the console can show instead
 * of leaving the public page blank.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PageContentService {

    private static final int MAX_PARAGRAPH = 4000;
    private static final int MAX_FIELD = 400;
    private static final int MAX_PAYLOAD_CHARS = 200_000;
    private static final int MAX_DEPTH = 6;

    /** Mandatory top-level objects, and the arrays that must be present with 1..max entries. */
    private static final Map<ContentSection, List<String>> REQUIRED_OBJECTS = Map.of(
            ContentSection.ABOUT, List.of("hero", "story", "values", "timeline"),
            ContentSection.CONTACT, List.of("hero", "aside"));

    private static final Map<ContentSection, Map<String, Integer>> REQUIRED_ARRAYS = Map.of(
            ContentSection.ABOUT, Map.of("values.items", 8, "timeline.items", 12, "story.points", 6),
            ContentSection.CONTACT, Map.of("cards", 6));

    /** Text fields that must not be blank, so a page can never be saved without its headings. */
    private static final Map<ContentSection, List<String>> REQUIRED_TEXT = Map.of(
            ContentSection.ABOUT, List.of("hero.title", "hero.lede", "story.heading", "values.heading",
                    "timeline.heading", "values.items[].title", "timeline.items[].title"),
            ContentSection.CONTACT, List.of("hero.title", "hero.lede", "cards[].label", "cards[].value"));

    private final PageContentRepository contentRepo;
    private final AdminRepository adminRepo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<PageContent> find(ContentSection section) {
        return contentRepo.findBySection(section);
    }

    @Transactional(readOnly = true)
    public PageContent getOrThrow(ContentSection section) {
        return contentRepo.findBySection(section)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No content stored for section " + section + "."));
    }

    /** Reads a stored payload back as JSON. The column is validated on write, so this cannot fail. */
    @Transactional(readOnly = true)
    public JsonNode payloadOf(PageContent content) {
        try {
            return objectMapper.readTree(content.getPayload());
        } catch (JacksonException ex) {
            throw new IllegalStateException("Stored content for " + content.getSection() + " is not valid JSON", ex);
        }
    }

    public PageContent save(ContentSection section, JsonNode payload, Long adminId) {
        String serialised = validateAndSerialise(section, payload);

        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));

        PageContent content = contentRepo.findBySection(section).orElseGet(PageContent::new);
        content.setSection(section);
        content.setPayload(serialised);
        content.setUpdatedBy(admin);

        return contentRepo.save(content);
    }

    /** Creates the row from the bundled default when it is missing. Used at startup. */
    public PageContent seed(ContentSection section, String defaultJson, Long adminId) {
        return contentRepo.findBySection(section).orElseGet(() -> save(section, read(defaultJson), adminId));
    }

    // ------------------------------------------------------------------ validation

    private String validateAndSerialise(ContentSection section, JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new BadRequestException("The content payload must be a JSON object.");
        }

        List<String> problems = new ArrayList<>();
        REQUIRED_OBJECTS.getOrDefault(section, List.of())
                .forEach(key -> requireObject(payload, key, problems));
        REQUIRED_ARRAYS.getOrDefault(section, Map.of())
                .forEach((path, max) -> requireArray(payload, path, max, problems));

        walk(payload, "", 0, problems);

        requiredText(section).forEach(path -> {
            JsonNode node = readPath(payload, path);
            if (node == null || !node.isTextual() || node.asText().isBlank()) {
                problems.add(path + " is required");
            }
        });

        if (problems.isEmpty() && serialise(payload).length() > MAX_PAYLOAD_CHARS) {
            problems.add("the content is too large (limit " + MAX_PAYLOAD_CHARS + " characters)");
        }

        if (!problems.isEmpty()) {
            throw new BadRequestException("Content for " + section + " was not saved: "
                    + String.join("; ", problems.stream().distinct().toList()) + ".");
        }

        return serialise(payload);
    }

    /** Every leaf must be text, and text has a budget, so one field cannot swallow the page. */
    private void walk(JsonNode node, String path, int depth, List<String> problems) {
        if (depth > MAX_DEPTH) {
            problems.add(path + " is nested too deeply");
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                walk(field.getValue(), path.isEmpty() ? field.getKey() : path + "." + field.getKey(), depth + 1, problems);
            }
            return;
        }

        if (node.isArray()) {
            if (node.size() > 24) {
                problems.add(path + " has too many entries (limit 24)");
            }
            node.values().forEach(child -> walk(child, path + "[]", depth + 1, problems));
            return;
        }

        if (node.isTextual()) {
            int limit = path.endsWith("lede") || path.endsWith("text") || path.endsWith("content")
                    ? MAX_PARAGRAPH : MAX_FIELD;
            if (node.asText().length() > limit) {
                problems.add(path + " is longer than " + limit + " characters");
            }
            return;
        }

        if (!node.isNull()) {
            problems.add(path + " must be text");
        }
    }

    private void requireObject(JsonNode payload, String key, List<String> problems) {
        JsonNode node = payload.get(key);
        if (node == null || !node.isObject()) {
            problems.add(key + " is required");
        }
    }

    private void requireArray(JsonNode payload, String path, int max, List<String> problems) {
        JsonNode node = readPath(payload, path);
        if (node == null || !node.isArray()) {
            problems.add(path + " must be a list");
            return;
        }
        if (node.isEmpty()) {
            problems.add(path + " needs at least one entry");
        } else if (node.size() > max) {
            problems.add(path + " accepts at most " + max + " entries");
        }
    }

    /** Resolves "story.points" and "values.items[]" style paths. */
    private JsonNode readPath(JsonNode root, String path) {
        JsonNode node = root;
        for (String segment : path.split("\\.")) {
            boolean every = segment.endsWith("[]");
            String key = every ? segment.substring(0, segment.length() - 2) : segment;
            node = node == null ? null : node.get(key);
            if (node == null) return null;
            if (every && node.isArray() && !node.isEmpty()) {
                node = node.get(0);
            }
        }
        return node;
    }

    private List<String> requiredText(ContentSection section) {
        return REQUIRED_TEXT.getOrDefault(section, List.of());
    }

    private String serialise(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            throw new BadRequestException("The content payload could not be read.");
        }
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Bundled default content is not valid JSON", ex);
        }
    }
}
