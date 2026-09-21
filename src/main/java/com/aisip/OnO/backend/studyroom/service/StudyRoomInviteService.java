package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomInviteCode;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomInviteCodeRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class StudyRoomInviteService {

    private static final int INVITE_CODE_RETRY_COUNT = 5;

    private final StudyRoomAccessService accessService;
    private final StudyRoomInviteCodeRepository inviteCodeRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StudyRoomService studyRoomService;
    private final StudyRoomInviteCodeIssuer inviteCodeIssuer;

    public InviteCodeResponse issueInviteCode(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        LocalDateTime now = LocalDateTime.now();
        return inviteCodeRepository.findTopByRoomIdOrderByExpiredAtDesc(roomId)
                .filter(code -> !code.isExpired(now))
                .map(code -> new InviteCodeResponse(code.getCode(), code.getExpiredAt()))
                .orElseGet(() -> createInviteCode(roomId, now));
    }

    /**
     * 초대 코드로 스터디룸에 참여한다.
     *
     * <p>격리 수준을 READ COMMITTED 로 낮춘 이유가 있다. MySQL 기본값인 REPEATABLE READ 에서는
     * 트랜잭션의 첫 일반 SELECT(여기서는 {@code findByCode}) 시점에 read view 가 고정되고,
     * 그 뒤의 일반 SELECT 는 락을 잡은 뒤라도 계속 그 스냅샷을 본다.
     * 그래서 {@code lockRoom} 으로 방 행을 잠가도 이어지는 {@code existsBy...}/{@code countBy...} 가
     * 먼저 커밋된 참여자를 보지 못했다. 실제로 8명이 마지막 한 자리에 동시에 들어오면
     * 여덟 명 전원이 "아직 19명"을 읽고 모두 입장해 정원 20명이 27명이 됐고,
     * 같은 사용자가 같은 코드를 연타하면 유니크 제약에 걸려 500 이 나갔다.
     * READ COMMITTED 는 문 단위로 스냅샷을 새로 뜨므로, 락을 얻은 뒤의 검사가 최신 상태를 본다.
     *
     * <p>락 순서는 항상 방 → 사용자다. 다른 경로({@code createRoom} 은 사용자만,
     * {@code leaveRoom}/{@code deleteRoom} 은 방만)와 순서가 어긋나지 않아 교착이 생기지 않는다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StudyRoomDetailResponse join(StudyRoomJoinRequest request, Long userId) {
        if (request.code() == null || !request.code().matches("\\d{6}")) {
            throw new ApplicationException(StudyRoomErrorCase.INVITE_CODE_INVALID);
        }
        StudyRoomInviteCode inviteCode = inviteCodeRepository.findByCode(request.code())
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.INVITE_CODE_INVALID));
        if (inviteCode.isExpired(LocalDateTime.now())) {
            throw new ApplicationException(StudyRoomErrorCase.INVITE_CODE_EXPIRED);
        }
        StudyRoom room = studyRoomService.lockRoom(inviteCode.getRoom().getId());
        if (memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new ApplicationException(StudyRoomErrorCase.ALREADY_MEMBER);
        }
        if (memberRepository.countByRoomId(room.getId()) >= StudyRoomService.MAX_ROOM_MEMBER_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_FULL);
        }
        // 참여 방 수 상한은 방이 아니라 사용자 단위 제약이라 방 락으로는 지켜지지 않는다.
        // 서로 다른 방에 동시에 들어가면 모두가 같은 개수를 읽고 통과하므로,
        // 검사 전에 사용자 행을 잠가 같은 사용자의 참여 요청을 직렬화한다.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        if (memberRepository.countByUserId(userId) >= StudyRoomService.MAX_USER_ROOM_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_LIMIT_EXCEEDED);
        }
        room.addMember(StudyRoomMember.create(user, StudyRoomMemberRole.MEMBER));
        return studyRoomService.buildDetail(room);
    }

    private InviteCodeResponse createInviteCode(Long roomId, LocalDateTime now) {
        for (int attempt = 0; attempt < INVITE_CODE_RETRY_COUNT; attempt++) {
            try {
                return inviteCodeIssuer.createOrReuse(roomId, now);
            } catch (DataIntegrityViolationException ignored) {
                if (attempt == INVITE_CODE_RETRY_COUNT - 1) {
                    throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
                }
            }
        }
        throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
    }
}
