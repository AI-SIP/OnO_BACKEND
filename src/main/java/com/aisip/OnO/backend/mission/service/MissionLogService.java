package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionMetric;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.practicenote.entity.PracticeNote;
import com.aisip.OnO.backend.practicenote.repository.PracticeNoteRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자의 행동 기록({@code mission_log})을 남긴다.
 *
 * <p><b>자동 적립은 요청이 온 앱 버전에 따라 갈린다.</b> 미션을 받을 수 있는 앱이면 끄고, 아니면 켠다.
 * 판정은 {@link LegacyAccrualPolicy} 한 곳에 있고, 미션 진행도 증가도 같은 판정을 본다.
 *
 * <p>가르는 이유는 켜져 있으면 같은 XP 가 두 경로로 들어오기 때문이다. 오답노트를 하나 쓰면
 * 자동 적립 +10 이 조용히 들어가고 미션 "오늘의 오답"을 받으면 +10 이 또 들어가,
 * 화면의 {@code +10 XP} 와 실제 증가량 20 이 어긋난다. 하루 200점 상한도 자동 적립에만 걸려
 * 같은 이름의 XP 가 출처에 따라 다른 규칙으로 움직인다.
 *
 * <p>그렇다고 <b>플래그 하나로 전체를 끌 수는 없다.</b> 적립과 미션 진행도는 서버가 올리지만
 * <b>받는 것은 앱이 한다.</b> 미션 화면이 없는 구버전 앱은 {@code claim} 을 부를 방법이 없어,
 * 통째로 끄면 구버전 사용자는 공부를 해도 XP 가 한 점도 안 쌓인다. 진행도만 쌓이고 레벨은 멈춘다.
 * 스토어 심사와 강제 업데이트 없이는 구버전이 한동안 남으므로, 요청마다 앱 버전을 보고 가른다.
 *
 * <p>어느 쪽이든 <b>기록은 그대로 남는다.</b> DAU·순 방문자·복습 로그 같은 관리자 통계가 전부 이 테이블을 읽는다.
 * 행이 사라지면 그 지표들이 통째로 0 이 된다. 중복 방지 판정도 설정과 무관하게 돈다.
 * <b>자동 적립이 도는 요청에서는 미션 진행도가 오르지 않는다.</b> 같은 행동으로 적립과 미션 보상을
 * 둘 다 받지 않게 하기 위해서다. 진행도를 막는 것은 {@link MissionProgressUpdater} 가 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MissionLogService {

    private final MissionLogRepository missionLogRepository;

    private final UserRepository userRepository;

    private final PracticeNoteRepository practiceNoteRepository;

    private final MissionProgressUpdater missionProgressUpdater;

    private final LegacyAccrualPolicy legacyAccrualPolicy;

    private static final Long DAILY_MISSION_POINT_LIMIT = 200L;

    /**
     * 미션 적립 전에 사용자 행을 먼저 잠근다.
     *
     * <p>두 가지를 동시에 막는다.
     *
     * <p>첫째, <b>교착</b>. mission_log 는 user 를 참조하므로 INSERT 시 InnoDB 가 부모 행에 공유 잠금을 건다.
     * 그 뒤 적립 포인트를 반영하려고 같은 행을 UPDATE 하면 공유 잠금을 배타 잠금으로 승격해야 하는데,
     * 같은 사용자의 요청이 동시에 들어오면 서로 상대의 공유 잠금 때문에 승격하지 못해 교착이 난다.
     * 실제로 같은 사용자가 미션을 동시에 8번 적립하면
     * {@code Deadlock found when trying to get lock} 이 그대로 500 으로 나갔다.
     * 처음부터 배타 잠금을 잡으면 승격 자체가 없어 교착이 생기지 않는다.
     * (자동 적립을 걷어내면서 사용자 행 UPDATE 가 없어져 이 승격 경로는 사라졌다. 기록으로 남겨 둔다.)
     *
     * <p>둘째, <b>중복 적립</b>. "오늘 이미 했는가"를 확인한 뒤 적립하는 check-then-act 구조라,
     * 잠금이 없으면 동시 요청이 모두 "아직 안 했다"를 읽고 각자 적립한다.
     * 사용자 단위로 직렬화하면 뒤에 온 요청은 앞선 적립을 보고 건너뛴다.
     */
    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
    }

    public void registerLoginMission(Long userId) {
        User user = lockUser(userId);
        boolean alreadyLogin = missionLogRepository.alreadyLogin(userId);

        if(!alreadyLogin) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.USER_LOGIN)
                    .build();

            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);

            // 출석 미션 진행도. 기존 적립 규칙은 그대로 두고, "오늘 첫 로그인" 판정만 그대로 빌려 쓴다.
            // 이 분기 밖에서 올리면 앱을 열 때마다 주간 출석 미션이 하루에 5까지 차 버린다.
            missionProgressUpdater.increase(userId, MissionMetric.LOGIN_DAY);
        }
    }

    public void registerProblemWriteMission(Long userId) {
        User user = lockUser(userId);
        boolean alreadyWriteMoreThanThreeProblems = missionLogRepository.alreadyWriteProblemsTodayMoreThan3(userId);

        if(!alreadyWriteMoreThanThreeProblems) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.PROBLEM_WRITE)
                    .build();

            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }
    }

    public void registerProblemWriteMissionBatch(Long userId, int count) {
        User user = lockUser(userId);
        long todayCount = missionLogRepository.countProblemWritesToday(userId);
        int toCreate = (int) Math.min(count, Math.max(0, 3 - todayCount));
        if (toCreate == 0) return;

        MissionRegisterDto dto = MissionRegisterDto.builder()
                .userId(userId)
                .missionType(MissionType.PROBLEM_WRITE)
                .build();

        for (int i = 0; i < toCreate; i++) {
            MissionLog log = MissionLog.from(dto, user);
            missionLogRepository.save(log);
            addPointToUser(user, log);
        }
    }

    public void registerProblemPracticeMission(Long userId, Long problemId) {
        User user = lockUser(userId);
        boolean alreadyPracticeProblem = missionLogRepository.alreadyPracticeProblem(problemId);

        if(!alreadyPracticeProblem) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.PROBLEM_PRACTICE)
                    .referenceId(problemId)
                    .build();

            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }
    }

    public void registerNotePracticeMission(Long userId, Long practiceNoteId) {
        User user = lockUser(userId);
        boolean alreadyPracticeNote = missionLogRepository.alreadyPracticeNote(practiceNoteId);

        if(!alreadyPracticeNote) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.NOTE_PRACTICE)
                    .referenceId(practiceNoteId)
                    .build();

            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);

            // 세트 완료 미션 진행도. 출석과 같은 이유로 기존 중복 방지 가드 안에 둔다.
            // 밖에서 올리면 같은 세트에 완료 요청을 세 번 보내는 것만으로 주간 세트 미션이 채워진다.
            missionProgressUpdater.increase(userId, MissionMetric.PRACTICE_NOTE_COMPLETED);
        }
    }

    /**
     * 행동 자체에 대한 자동 적립.
     *
     * <p>적립하지 않기로 하면 아무것도 하지 않는다. 이 메서드만 비는 것이지 호출부의 기록 저장과
     * 중복 방지 판정은 그대로 돈다. 진행도 증가는 반대로 적립하는 요청에서만 막힌다.
     *
     * <p>하루 200점 상한은 이 경로에만 있는 규칙이다. 미션 보상은 {@link MissionRewardGranter} 가
     * 따로 지급하고 상한을 타지 않는다.
     */
    private Long addPointToUser(User user, MissionLog missionLog) {
        // 비상 스위치가 꺼졌거나 미션을 받을 수 있는 앱이면 보상 경로 하나만 남긴다.
        if (!legacyAccrualPolicy.accruesForCurrentRequest()) {
            return 0L;
        }

        Long pointToday = missionLogRepository.getPointSumToday(user.getId());
        if (pointToday <= DAILY_MISSION_POINT_LIMIT) {
            Long point = Math.min(missionLog.getPoint(), DAILY_MISSION_POINT_LIMIT - pointToday);

            // 미션 타입에 따라 적절한 능력치에 경험치 적용
            switch (missionLog.getMissionType().getAbilityType()) {
                case ATTENDANCE -> user.getUserMissionStatus().gainAttendancePoint(point);
                case NOTE_WRITE -> user.getUserMissionStatus().gainNoteWritePoint(point);
                case PROBLEM_PRACTICE -> user.getUserMissionStatus().gainProblemPracticePoint(point);
                case NOTE_PRACTICE -> user.getUserMissionStatus().gainNotePracticePoint(point);
            }

            return point;
        }
        return 0L;
    }

    @Transactional(readOnly = true)
    public List<MissionLog> findAllByUserId(Long userId) {
        return missionLogRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyActiveUsersCount(int days) {
        return missionLogRepository.getDailyActiveUsersCount(days);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyActiveUsersCount(LocalDate startDate, LocalDate endDate) {
        return missionLogRepository.getDailyActiveUsersCount(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyVisitCount(LocalDate startDate, LocalDate endDate) {
        return getDailyMissionCount(MissionType.USER_LOGIN, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public long countUniqueVisitors(LocalDate startDate, LocalDate endDate) {
        return missionLogRepository.countDistinctUsersByMissionTypeAndCreatedAtBetween(
                MissionType.USER_LOGIN,
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.MAX)
        );
    }

    @Transactional(readOnly = true)
    public List<com.aisip.OnO.backend.user.entity.User> getActiveUsersByDate(LocalDate date) {
        return missionLogRepository.getActiveUsersByDate(date);
    }

    @Transactional(readOnly = true)
    public Page<AdminPracticeLogResponseDto> findAdminPracticeLogs(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<MissionLog> missionLogs = missionLogRepository.findAllByMissionTypeWithUser(MissionType.NOTE_PRACTICE, pageRequest);
        long total = missionLogRepository.countByMissionType(MissionType.NOTE_PRACTICE);
        List<Long> practiceNoteIds = missionLogs.stream()
                .map(MissionLog::getReferenceId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, PracticeNote> practiceNotesById = practiceNoteRepository.findAllById(practiceNoteIds).stream()
                .collect(Collectors.toMap(PracticeNote::getId, Function.identity()));

        List<AdminPracticeLogResponseDto> content = missionLogs.stream()
                .map(missionLog -> AdminPracticeLogResponseDto.from(
                        missionLog,
                        practiceNotesById.get(missionLog.getReferenceId())
                ))
                .toList();

        return new PageImpl<>(content, pageRequest, total);
    }

    @Transactional(readOnly = true)
    public long countNotePracticeLogs() {
        return missionLogRepository.countByMissionType(MissionType.NOTE_PRACTICE);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyNotePracticeLogsCount(LocalDate startDate, LocalDate endDate) {
        return getDailyMissionCount(MissionType.NOTE_PRACTICE, startDate, endDate);
    }

    private Map<LocalDate, Long> getDailyMissionCount(MissionType missionType, LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        missionLogRepository.countDailyByMissionType(
                        missionType,
                        startDate.atStartOfDay(),
                        endDate.atTime(LocalTime.MAX)
                )
                .forEach(row -> result.put(toLocalDate(row[0]), (Long) row[1]));

        Map<LocalDate, Long> orderedResult = new LinkedHashMap<>();
        for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
            orderedResult.put(date, result.getOrDefault(date, 0L));
        }

        return orderedResult;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }
}
