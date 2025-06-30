package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.entity.MissionType;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionLogService {

    private final MissionLogRepository missionLogRepository;

    private final UserRepository userRepository;

    private static final Long DAILY_MISSION_POINT_LIMIT = 100L;

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

            addPointToUser(user, missionLog);
        }
    }

    private Long addPointToUser(User user, MissionLog missionLog) {
        Long pointToday = missionLogRepository.getPointSumToday(user.getId());
        if(pointToday <= DAILY_MISSION_POINT_LIMIT) {
            Long point = getMin(missionLog.getPoint(), DAILY_MISSION_POINT_LIMIT - pointToday);
            user.getUserMissionStatus().gainPoint(point);

            return point;
        } else {
            return 0L;
        }
    }

    private Long getMin(Long p1, Long p2) {
        return p1 > p2 ? p2 : p1;
    }
}
