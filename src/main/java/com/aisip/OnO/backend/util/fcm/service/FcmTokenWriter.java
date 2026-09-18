package com.aisip.OnO.backend.util.fcm.service;

import com.aisip.OnO.backend.util.fcm.dto.FcmTokenRequestDto;
import com.aisip.OnO.backend.util.fcm.entity.FcmToken;
import com.aisip.OnO.backend.util.fcm.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 토큰 한 건의 등록을 <b>독립된 트랜잭션</b>으로 수행한다.
 *
 * <p>등록은 (user_id, token) 유니크 인덱스와 경쟁하는 작업이라
 * "조회 → 없으면 삽입"이 한 트랜잭션 안에서는 안전하게 복구되지 않는다.
 * 삽입이 Duplicate entry 로 실패하는 순간 그 트랜잭션은 롤백 대상이 되고,
 * 그 안에서 예외를 잡아도 rollback-only 로 표시돼 커밋 시점에 터진다.
 * MySQL REPEATABLE READ 에서는 같은 트랜잭션으로 다시 조회해도
 * 스냅샷 때문에 남이 커밋한 행이 보이지 않는다.
 *
 * <p>그래서 중복 판단은 {@link FcmService} 가 트랜잭션 <b>바깥</b>에서 하고,
 * 여기서는 매 호출마다 새 트랜잭션(= 새 스냅샷)을 연다. 태그 생성의 {@code TagWriter} 와 같은 구조다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FcmTokenWriter {

    private final FcmTokenRepository fcmTokenRepository;

    /**
     * 이전 소유자 행을 지우고, 없으면 토큰 행을 새로 넣는다.
     *
     * <p>유니크 인덱스에 걸리면 {@link org.springframework.dao.DataIntegrityViolationException} 이
     * 이 트랜잭션 밖으로 그대로 올라간다. 호출자가 새 트랜잭션에서 복구한다.
     * 이때 이 트랜잭션이 통째로 롤백되므로 이전 소유자 행 삭제도 함께 되돌아가는데,
     * 삭제를 되살리는 것은 호출자의 재시도 몫이다.
     *
     * <p>{@code saveAndFlush} 로 삽입을 트랜잭션 안에서 앞당긴다. 커밋 시점 flush 까지 미루면
     * 실패 지점이 트랜잭션 경계와 겹쳐, 같은 메서드 안에서 실패 여부를 판단할 수 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void register(FcmTokenRequestDto fcmTokenRequestDto, Long userId) {
        List<FcmToken> previousOwnerTokens =
                fcmTokenRepository.findAllByTokenAndUserIdNot(fcmTokenRequestDto.token(), userId);
        if (!previousOwnerTokens.isEmpty()) {
            fcmTokenRepository.deleteAllInBatch(previousOwnerTokens);
            log.info("FCM token moved to another user - userId: {}, removedPreviousOwnerRows: {}",
                    userId, previousOwnerTokens.size());
        }

        if (!fcmTokenRepository.existsByUserIdAndToken(userId, fcmTokenRequestDto.token())) {
            fcmTokenRepository.saveAndFlush(FcmToken.From(fcmTokenRequestDto, userId));
        }
    }

    /** 새 트랜잭션에서 읽는다. 실패한 트랜잭션의 스냅샷으로는 남이 커밋한 행이 보이지 않기 때문이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean exists(Long userId, String token) {
        return fcmTokenRepository.existsByUserIdAndToken(userId, token);
    }
}
