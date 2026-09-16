package com.example.PrathibaLanka.config;

import com.example.PrathibaLanka.service.MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

/**
 * Serves uploaded media from the file system. File names are generated UUIDs that never change, so
 * the response can be cached hard.
 */
@Configuration
@RequiredArgsConstructor
public class MediaWebConfig implements WebMvcConfigurer {

    private final MediaStorageService storage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(storage.getUrlPrefix() + "/**")
                .addResourceLocations(storage.getRoot().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
