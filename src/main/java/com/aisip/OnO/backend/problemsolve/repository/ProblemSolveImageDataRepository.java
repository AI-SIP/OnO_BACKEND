package com.aisip.OnO.backend.problemsolve.repository;

import com.aisip.OnO.backend.problemsolve.entity.ProblemSolveImageData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProblemSolveImageDataRepository extends JpaRepository<ProblemSolveImageData, Long> {

    List<ProblemSolveImageData> findAllByProblemSolveId(Long problemSolveId);

    boolean existsByImageUrl(String imageUrl);

    /** 이 사용자의 풀이 기록에 붙은 행만 돌려준다. 같은 URL 을 가진 다른 사용자의 행은 빠진다. */
    @Query("""
            select i from ProblemSolveImageData i join fetch i.problemSolve s
            where i.imageUrl = :imageUrl and s.userId = :userId and s.deletedAt is null
            """)
    List<ProblemSolveImageData> findAllByImageUrlAndUserId(@Param("imageUrl") String imageUrl, @Param("userId") Long userId);

    @Query("""
            select distinct i.imageUrl from ProblemSolveImageData i join i.problemSolve s
            where i.imageUrl in :imageUrls and s.userId <> :userId and s.deletedAt is null
            """)
    List<String> findImageUrlsUsedByOtherUsers(@Param("imageUrls") Collection<String> imageUrls, @Param("userId") Long userId);
}
