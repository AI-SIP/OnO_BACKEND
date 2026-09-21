package com.aisip.OnO.backend.tag.repository;

import com.aisip.OnO.backend.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByUserIdAndNormalizedName(Long userId, String normalizedName);

    List<Tag> findAllByUserIdOrderByNameAsc(Long userId);

    List<Tag> findAllByIdInAndUserId(List<Long> ids, Long userId);

    /**
     * 소프트 삭제된 동명 태그를 되살린다.
     *
     * <p>{@code idx_tag_user_normalized} 는 {@code deleted_at} 을 포함하지 않아
     * 삭제된 행도 이름을 계속 점유한다. 그래서 "삭제 후 같은 이름으로 재생성"이
     * Duplicate entry 로 실패하는데, 새로 넣는 대신 기존 행을 복구해 이를 피한다.
     *
     * <p>{@code @SQLRestriction("deleted_at IS NULL")} 때문에 JPQL 로는 삭제된 행에
     * 접근할 수 없어 네이티브 쿼리를 쓴다.
     *
     * @return 복구된 행 수 (0 이면 되살릴 태그가 없었다는 뜻)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE tag SET deleted_at = NULL, name = :name, updated_at = NOW(6) "
            + "WHERE user_id = :userId AND normalized_name = :normalizedName AND deleted_at IS NOT NULL",
            nativeQuery = true)
    int restoreDeletedTag(@Param("userId") Long userId,
                          @Param("name") String name,
                          @Param("normalizedName") String normalizedName);
}
