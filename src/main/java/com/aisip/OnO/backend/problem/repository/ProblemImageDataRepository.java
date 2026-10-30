package com.aisip.OnO.backend.problem.repository;

import com.aisip.OnO.backend.problem.entity.ProblemImageData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProblemImageDataRepository extends JpaRepository<ProblemImageData, Long> {

    List<ProblemImageData> findAllByProblemId(Long problemId);

    void deleteAllByProblemId(Long problemId);

    boolean existsByImageUrl(String imageUrl);

    /** 이 사용자의 문제에 붙은 행만 돌려준다. 같은 URL 을 가진 다른 사용자의 행은 빠진다. */
    @Query("""
            select i from ProblemImageData i join fetch i.problem p
            where i.imageUrl = :imageUrl and p.userId = :userId and p.deletedAt is null
            """)
    List<ProblemImageData> findAllByImageUrlAndUserId(@Param("imageUrl") String imageUrl, @Param("userId") Long userId);

    @Query("""
            select distinct i.imageUrl from ProblemImageData i join i.problem p
            where i.imageUrl in :imageUrls and p.userId <> :userId and p.deletedAt is null
            """)
    List<String> findImageUrlsUsedByOtherUsers(@Param("imageUrls") Collection<String> imageUrls, @Param("userId") Long userId);
}
