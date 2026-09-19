package com.example.PrathibaLanka.service;

import com.example.PrathibaLanka.entity.Admin;
import com.example.PrathibaLanka.entity.MediaAsset;
import com.example.PrathibaLanka.enums.MediaType;
import com.example.PrathibaLanka.exception.ConflictException;
import com.example.PrathibaLanka.exception.ResourceNotFoundException;
import com.example.PrathibaLanka.repository.AdminRepository;
import com.example.PrathibaLanka.repository.GalleryImageRepository;
import com.example.PrathibaLanka.repository.JournalPostRepository;
import com.example.PrathibaLanka.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Keeps the media rows and the files on disk in step. */
@Service
@RequiredArgsConstructor
@Transactional
public class MediaService {

    private final MediaAssetRepository mediaRepo;
    private final GalleryImageRepository galleryRepo;
    private final JournalPostRepository journalRepo;
    private final AdminRepository adminRepo;
    private final MediaStorageService storage;

    public MediaAsset upload(MultipartFile file, String title, Long adminId) {
        Admin admin = adminRepo.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));

        MediaStorageService.StoredFile stored = storage.store(file);

        MediaAsset asset = new MediaAsset();
        asset.setOriginalName(file.getOriginalFilename() == null ? stored.storedName() : file.getOriginalFilename());
        asset.setStoredName(stored.storedName());
        asset.setContentType(stored.contentType());
        asset.setMediaType(stored.mediaType());
        asset.setSizeBytes(stored.sizeBytes());
        asset.setUrl(stored.url());
        asset.setTitle(title == null || title.isBlank() ? null : title.trim());
        asset.setUploadedBy(admin);

        return mediaRepo.save(asset);
    }

    /**
     * Deletes a file that the public site no longer uses. A file still referenced by a gallery item
     * or a journal cover is refused, so a page cannot end up pointing at a missing file.
     */
    public void delete(Long mediaId) {
        MediaAsset asset = getById(mediaId);

        boolean usedByGallery = galleryRepo.existsByImageUrl(asset.getUrl());
        boolean usedByJournal = journalRepo.existsByCoverImageUrl(asset.getUrl());
        if (usedByGallery || usedByJournal) {
            throw new ConflictException("This file is still used by "
                    + (usedByGallery ? "a gallery item" : "a journal cover")
                    + ". Remove it there first.");
        }

        mediaRepo.delete(asset);
        storage.delete(asset.getStoredName());
    }

    @Transactional(readOnly = true)
    public MediaAsset getById(Long mediaId) {
        return mediaRepo.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found with id: " + mediaId));
    }

    @Transactional(readOnly = true)
    public List<MediaAsset> list(MediaType type) {
        return type == null ? mediaRepo.findAllByOrderByMediaIdDesc() : mediaRepo.findByMediaTypeOrderByMediaIdDesc(type);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> limits() {
        return storage.describeLimits();
    }
}
