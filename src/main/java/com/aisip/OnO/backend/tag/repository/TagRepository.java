package com.aisip.OnO.backend.tag.repository;

import com.aisip.OnO.backend.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByUserIdAndNormalizedName(Long userId, String normalizedName);

    List<Tag> findAllByUserIdOrderByNameAsc(Long userId);
}
