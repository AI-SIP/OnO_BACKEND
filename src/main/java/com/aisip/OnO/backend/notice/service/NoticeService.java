package com.aisip.OnO.backend.notice.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.notice.dto.NoticeCreateRequestDto;
import com.aisip.OnO.backend.notice.dto.NoticeResponseDto;
import com.aisip.OnO.backend.notice.entity.NoticeType;
import com.aisip.OnO.backend.notice.entity.ServiceNotice;
import com.aisip.OnO.backend.notice.exception.NoticeErrorCase;
import com.aisip.OnO.backend.notice.repository.ServiceNoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    // 서비스의 하루 기준은 KST 다. 인자 없는 now() 는 JVM 기본 시간대를 쓰기 때문에
    // 서버 시간대 설정이 빠지는 순간 공지 노출 기간이 밀린다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final int DEFAULT_DURATION_HOURS = 24;
    private static final int MAX_DURATION_HOURS = 168;
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final ServiceNoticeRepository serviceNoticeRepository;
    private final NoticeCacheService noticeCacheService;
    private final NoticeDismissService noticeDismissService;

    /**
     * 유저에게 지금 보여줄 공지를 돌려준다. 없거나 이미 그만 보기를 누른 공지면 null 이다.
     */
    @Transactional(readOnly = true)
    public NoticeResponseDto findActiveNoticeForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now(KST);

        // 캐시가 비어 있는 것(미스)과 "활성 공지가 없다"고 캐싱된 것을 구분해야 해서
        // Optional.map 으로 잇지 않고 isPresent 로 가른다. map 을 쓰면 공지 없음이
        // 캐시 미스로 흘러 들어가 매번 DB 를 보게 된다.
        Optional<NoticeCacheService.ActiveNoticeSnapshot> cached = noticeCacheService.read(now);
        NoticeResponseDto activeNotice = cached.isPresent()
                ? cached.get().notice()
                : loadAndCacheActiveNotice(now);

        if (activeNotice == null) {
            return null;
        }
        if (noticeDismissService.isDismissed(activeNotice.noticeId(), userId)) {
            return null;
        }
        return activeNotice;
    }

    /**
     * 그만 보기를 눌렀을 때 그 유저에게만 24시간 동안 숨긴다.
     */
    @Transactional(readOnly = true)
    public void dismissNotice(Long noticeId, Long userId) {
        ServiceNotice notice = serviceNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new ApplicationException(NoticeErrorCase.NOTICE_NOT_FOUND));

        Duration remaining = Duration.between(LocalDateTime.now(KST), notice.getExpiresAt());
        noticeDismissService.dismiss(noticeId, userId, remaining);
    }

    /**
     * 관리자 화면에서 쓰는 조회. 그만 보기와 무관하게 지금 걸려 있는 공지를 그대로 보여준다.
     */
    @Transactional(readOnly = true)
    public NoticeResponseDto findActiveNoticeForAdmin() {
        return serviceNoticeRepository.findActiveNotices(LocalDateTime.now(KST)).stream()
                .findFirst()
                .map(NoticeResponseDto::from)
                .orElse(null);
    }

    /**
     * 공지를 등록한다. 활성 공지는 한 건만 두기로 했으므로 걸려 있던 공지는 함께 내린다.
     */
    @Transactional
    public NoticeResponseDto registerNotice(NoticeCreateRequestDto request) {
        String title = validateTitle(request.title());
        String content = validateContent(request.content());
        NoticeType type = validateType(request.type());
        int durationHours = validateDurationHours(request.durationHours());

        LocalDateTime now = LocalDateTime.now(KST);
        List<ServiceNotice> previousNotices = serviceNoticeRepository.findActiveNotices(now);
        previousNotices.forEach(serviceNoticeRepository::delete);

        ServiceNotice notice = serviceNoticeRepository.save(
                ServiceNotice.of(title, content, type, now, now.plusHours(durationHours))
        );
        evictCacheAfterCommit();

        log.info("공지를 등록했다. noticeId={}, expiresAt={}, 내린 공지 수={}",
                notice.getId(), notice.getExpiresAt(), previousNotices.size());
        return NoticeResponseDto.from(notice);
    }

    /**
     * 공지를 즉시 내린다. 행은 지우지 않고 soft delete 로 남긴다.
     */
    @Transactional
    public void removeNotice(Long noticeId) {
        ServiceNotice notice = serviceNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new ApplicationException(NoticeErrorCase.NOTICE_NOT_FOUND));

        serviceNoticeRepository.delete(notice);
        evictCacheAfterCommit();

        log.info("공지를 내렸다. noticeId={}", noticeId);
    }

    private NoticeResponseDto loadAndCacheActiveNotice(LocalDateTime now) {
        NoticeResponseDto notice = serviceNoticeRepository.findActiveNotices(now).stream()
                .findFirst()
                .map(NoticeResponseDto::from)
                .orElse(null);

        // 공지가 없다는 사실도 캐싱해야 빈 조회가 매번 DB 로 내려가지 않는다.
        noticeCacheService.write(notice);
        return notice;
    }

    /**
     * 커밋이 끝난 뒤에 캐시를 비운다.
     *
     * <p>트랜잭션 안에서 비우면 커밋 전에 들어온 조회가 아직 반영되지 않은 상태를
     * 다시 캐싱해서, 방금 등록하거나 내린 공지가 최대 TTL 동안 어긋난 채로 나간다.
     */
    private void evictCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            noticeCacheService.evict();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                noticeCacheService.evict();
            }
        });
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > MAX_TITLE_LENGTH) {
            throw new ApplicationException(NoticeErrorCase.NOTICE_TITLE_INVALID);
        }
        return title.trim();
    }

    private String validateContent(String content) {
        if (content == null || content.isBlank() || content.trim().length() > MAX_CONTENT_LENGTH) {
            throw new ApplicationException(NoticeErrorCase.NOTICE_CONTENT_INVALID);
        }
        return content.trim();
    }

    private NoticeType validateType(NoticeType type) {
        if (type == null) {
            throw new ApplicationException(NoticeErrorCase.NOTICE_TYPE_REQUIRED);
        }
        return type;
    }

    private int validateDurationHours(Integer durationHours) {
        if (durationHours == null) {
            return DEFAULT_DURATION_HOURS;
        }
        if (durationHours < 1 || durationHours > MAX_DURATION_HOURS) {
            throw new ApplicationException(NoticeErrorCase.NOTICE_DURATION_INVALID);
        }
        return durationHours;
    }
}
