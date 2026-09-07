package com.aisip.OnO.backend.tag.service;

import com.aisip.OnO.backend.tag.entity.Tag;
import com.aisip.OnO.backend.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 태그 한 건의 조회/삽입을 <b>각각 독립된 트랜잭션</b>으로 수행한다.
 *
 * <p>태그 생성은 (user_id, normalized_name) 유니크 인덱스와 경쟁하는 작업이라
 * "조회 → 없으면 삽입"이 한 트랜잭션 안에서는 안전하게 재시도되지 않는다.
 * 삽입이 Duplicate entry 로 실패하는 순간 그 트랜잭션은 롤백 대상이 되고,
 * MySQL REPEATABLE READ 에서는 같은 트랜잭션으로 다시 조회해도
 * 스냅샷 때문에 남이 커밋한 행이 보이지 않기 때문이다.
 *
 * <p>따라서 재시도 판단은 {@link TagService} 가 트랜잭션 <b>바깥</b>에서 하고,
 * 여기서는 매 호출마다 새 트랜잭션(= 새 스냅샷)을 연다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TagWriter {

    private final TagRepository tagRepository;

    /**
     * 살아있는 태그를 찾고, 없으면 소프트 삭제된 동명 태그를 되살린다.
     *
     * <p>{@code tag} 의 유니크 인덱스는 {@code deleted_at} 을 포함하지 않는다.
     * 즉 소프트 삭제된 행도 이름을 계속 점유하므로, 삭제한 태그와 같은 이름을
     * 다시 만들면 Duplicate entry 로 실패한다. 사용자 입장에서 "지웠다가 다시 만들기"는
     * 당연히 되어야 하는 동작이라 삭제된 행을 복구해서 돌려준다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Tag> findOrRestore(Long userId, String displayName, String normalizedName) {
        Optional<Tag> activeTag = tagRepository.findByUserIdAndNormalizedName(userId, normalizedName);
        if (activeTag.isPresent()) {
            return activeTag;
        }

        int restored = tagRepository.restoreDeletedTag(userId, displayName, normalizedName);
        if (restored == 0) {
            return Optional.empty();
        }

        log.info("userId: {} restored soft-deleted tag: {}", userId, displayName);
        return tagRepository.findByUserIdAndNormalizedName(userId, normalizedName);
    }

    /**
     * 태그를 새로 삽입한다. 유니크 인덱스에 걸리면
     * {@link org.springframework.dao.DataIntegrityViolationException} 이 그대로 올라간다.
     * 호출자가 이 트랜잭션 밖에서 재조회로 복구한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Tag insert(Long userId, String displayName, String normalizedName) {
        return tagRepository.saveAndFlush(Tag.from(userId, displayName, normalizedName));
    }
}
