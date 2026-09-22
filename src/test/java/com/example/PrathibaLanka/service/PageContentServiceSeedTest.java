package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.PageContent;
import com.example.PrathibaLanka.enums.ContentSection;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.PageContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Seeding the About and Contact pages has to survive a database with no admin account in it.
 *
 * <p>That is the state a first deployment is in when BOOTSTRAP_ADMIN_PASSWORD is left unset: no admin
 * is created, so there is no id to record as the author. Looking one up anyway called
 * {@code findById(null)}, which Spring Data rejects with "The given id must not be null" - thrown from
 * an ApplicationRunner, so the whole application failed to start on an otherwise healthy database.
 * The tests here run without a Spring context on purpose: this is about the service's own behaviour.
 */
class PageContentServiceSeedTest {

    private final PageContentRepository contentRepo = mock(PageContentRepository.class);
    private final AdminRepository adminRepo = mock(AdminRepository.class);

    private final PageContentService service =
            new PageContentService(contentRepo, adminRepo, JsonMapper.builder().build());

    @Test
    void seedsTheBundledDefaultWhenThereIsNoAdminToName() throws Exception {
        when(contentRepo.findBySection(ContentSection.ABOUT)).thenReturn(Optional.empty());
        when(contentRepo.save(any(PageContent.class))).thenAnswer(call -> call.getArgument(0));

        PageContent saved = service.seed(ContentSection.ABOUT, bundledDefault(ContentSection.ABOUT), null);

        assertThat(saved.getSection()).isEqualTo(ContentSection.ABOUT);
        // The bundled default is validated and stored, not written through untouched.
        assertThat(saved.getPayload()).contains("\"hero\"");
        assertThat(saved.getUpdatedBy()).isNull();
        // The lookup that used to be attempted with a null id.
        verifyNoInteractions(adminRepo);
    }

    @Test
    void leavesAnExistingRowAlone() throws Exception {
        PageContent existing = new PageContent();
        when(contentRepo.findBySection(ContentSection.CONTACT)).thenReturn(Optional.of(existing));

        assertThat(service.seed(ContentSection.CONTACT, bundledDefault(ContentSection.CONTACT), null))
                .isSameAs(existing);
        verify(contentRepo, never()).save(any(PageContent.class));
    }

    /** The same file the runner reads, so the test fails if a default stops matching its validator. */
    private static String bundledDefault(ContentSection section) throws Exception {
        String path = "content/" + section.name().toLowerCase() + ".json";
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
