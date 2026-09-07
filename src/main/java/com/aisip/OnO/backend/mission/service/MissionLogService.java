package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.admin.dto.AdminPracticeLogResponseDto;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
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
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionLogService {

    private final MissionLogRepository missionLogRepository;

    private final UserRepository userRepository;

    private final PracticeNoteRepository practiceNoteRepository;

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
     *
     * <p>둘째, <b>중복 적립</b>. "오늘 이미 했는가"를 확인한 뒤 적립하는 check-then-act 구조라,
     * 잠금이 없으면 동시 요청이 모두 "아직 안 했다"를 읽고 각자 적립한다.
     * 사용자 단위로 직렬화하면 뒤에 온 요청은 앞선 적립을 보고 건너뛴다.
     */
    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
    }

    public Long registerMissionLog(@NotNull MissionRegisterDto missionRegisterDto) {

        Long userId = missionRegisterDto.userId();

        // switch 가 MissionType 의 네 상수를 모두 다루고 있어 default 분기는 도달할 수 없었고,
        // missionType 이 null 이면 switch 자체가 NPE 를 던져 400 이어야 할 입력 오류가 500 으로 나갔다.
        // 잘못된 미션 종류는 MISSION_TYPE_NOT_FOUND(400) 로 거절한다.
        if (missionRegisterDto.missionType() == null) {
            throw new ApplicationException(MissionErrorCase.MISSION_TYPE_NOT_FOUND);
        }

        User user = lockUser(userId);

        boolean canNotRegister = switch (missionRegisterDto.missionType()) {
            case USER_LOGIN -> missionLogRepository.alreadyLogin(userId);
            case PROBLEM_WRITE -> missionLogRepository.alreadyWriteProblemsTodayMoreThan3(userId);
            case PROBLEM_PRACTICE -> missionLogRepository.alreadyPracticeProblem(missionRegisterDto.referenceId());
            case NOTE_PRACTICE -> missionLogRepository.alreadyPracticeNote(missionRegisterDto.referenceId());
            default -> throw new ApplicationException(MissionErrorCase.MISSION_TYPE_NOT_FOUND);
        };

        if(!canNotRegister) {
            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }

        return 0L;
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
        }
    }

    private Long addPointToUser(User user, MissionLog missionLog) {
        Long pointToday = missionLogRepository.getPointSumToday(user.getId());
        if(pointToday <= DAILY_MISSION_POINT_LIMIT) {
            Long point = getMin(missionLog.getPoint(), DAILY_MISSION_POINT_LIMIT - pointToday);

            // 미션 타입에 따라 적절한 능력치에 경험치 적용
            switch(missionLog.getMissionType().getAbilityType()) {
                case ATTENDANCE -> user.getUserMissionStatus().gainAttendancePoint(point);
                case NOTE_WRITE -> user.getUserMissionStatus().gainNoteWritePoint(point);
                case PROBLEM_PRACTICE -> user.getUserMissionStatus().gainProblemPracticePoint(point);
                case NOTE_PRACTICE -> user.getUserMissionStatus().gainNotePracticePoint(point);
            }

            return point;
        } else {
            return 0L;
        }
    }

    private Long getMin(Long p1, Long p2) {
        return p1 > p2 ? p2 : p1;
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
