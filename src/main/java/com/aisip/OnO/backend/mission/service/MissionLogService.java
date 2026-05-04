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

    public Long registerMissionLog(@NotNull MissionRegisterDto missionRegisterDto) {

        Long userId = missionRegisterDto.userId();
        boolean canNotRegister = true;
        canNotRegister = switch (missionRegisterDto.missionType()) {
            case USER_LOGIN -> missionLogRepository.alreadyLogin(userId);
            case PROBLEM_WRITE -> missionLogRepository.alreadyWriteProblemsTodayMoreThan3(userId);
            case PROBLEM_PRACTICE -> missionLogRepository.alreadyPracticeProblem(missionRegisterDto.referenceId());
            case NOTE_PRACTICE -> missionLogRepository.alreadyPracticeNote(missionRegisterDto.referenceId());
            default -> throw new ApplicationException(MissionErrorCase.MISSION_TYPE_NOT_FOUND);
        };

        if(!canNotRegister) {
            User user = userRepository.findById(userId).orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));

            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }

        return 0L;
    }

    public void registerLoginMission(Long userId) {
        boolean alreadyLogin = missionLogRepository.alreadyLogin(userId);

        if(!alreadyLogin) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.USER_LOGIN)
                    .build();

            User user = userRepository.findById(userId).orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }
    }

    public void registerProblemWriteMission(Long userId) {
        boolean alreadyWriteMoreThanThreeProblems = missionLogRepository.alreadyWriteProblemsTodayMoreThan3(userId);

        if(!alreadyWriteMoreThanThreeProblems) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.PROBLEM_WRITE)
                    .build();

            User user = userRepository.findById(userId).orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }
    }

    public void registerProblemPracticeMission(Long userId, Long problemId) {
        boolean alreadyPracticeProblem = missionLogRepository.alreadyPracticeProblem(problemId);

        if(!alreadyPracticeProblem) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.PROBLEM_PRACTICE)
                    .referenceId(problemId)
                    .build();

            User user = userRepository.findById(userId).orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            addPointToUser(user, missionLog);
        }
    }

    public void registerNotePracticeMission(Long userId, Long practiceNoteId) {
        boolean alreadyPracticeNote = missionLogRepository.alreadyPracticeNote(practiceNoteId);

        if(!alreadyPracticeNote) {
            MissionRegisterDto missionRegisterDto = MissionRegisterDto
                    .builder()
                    .userId(userId)
                    .missionType(MissionType.NOTE_PRACTICE)
                    .referenceId(practiceNoteId)
                    .build();

            User user = userRepository.findById(userId).orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));
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
