package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.PageContent;
import com.example.PrathibaLanka.enums.ContentSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PageContentRepository extends JpaRepository<PageContent, Long> {

    Optional<PageContent> findBySection(ContentSection section);
}
