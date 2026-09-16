package com.example.PrathibaLanka.repository;

import com.example.PrathibaLanka.entity.MediaAsset;
import com.example.PrathibaLanka.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByMediaTypeOrderByMediaIdDesc(MediaType mediaType);

    List<MediaAsset> findAllByOrderByMediaIdDesc();
}
