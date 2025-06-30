package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.mission.dto.MissionRegisterDto;
import com.aisip.OnO.backend.mission.entity.MissionLog;
import com.aisip.OnO.backend.mission.exception.MissionErrorCase;
import com.aisip.OnO.backend.mission.repository.MissionLogRepository;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MissionLogService {

    private final MissionLogRepository missionLogRepository;

    private final UserRepository userRepository;

    private static final Long DAILY_MISSION_POINT_LIMIT = 100L;

    public String registerMissionLog(MissionRegisterDto missionRegisterDto) {

        boolean canRegister = true;
        canRegister = switch (missionRegisterDto.missionType()) {
            case USER_LOGIN -> missionLogRepository.alreadyLogin(missionRegisterDto.userId());
            case PROBLEM_WRITE -> true;
            case PROBLEM_PRACTICE -> missionLogRepository.alreadyPracticeProblem(missionRegisterDto.referenceId());
            case NOTE_PRACTICE -> missionLogRepository.alreadyPracticeNote(missionRegisterDto.referenceId());
            default -> throw new ApplicationException(MissionErrorCase.MISSION_TYPE_NOT_FOUND);
        };

        if(canRegister) {
            Long userId = missionRegisterDto.userId();
            User user = userRepository.findById(userId).orElseThrow(() -> new ApplicationException(MissionErrorCase.USER_NOT_FOUND));

            MissionLog missionLog = MissionLog.from(missionRegisterDto, user);
            missionLogRepository.save(missionLog);

            Long pointToday = missionLogRepository.getPointSumToday(userId);
            if(pointToday <= DAILY_MISSION_POINT_LIMIT) {
                Long point = getMin(missionLog.getPoint(), DAILY_MISSION_POINT_LIMIT - pointToday);
                user.getUserMissionStatus().gainPoint(point);

                return point + "점을 획득했습니다!";
            } else {
                return "일일 포인트 최대치를 획득했습니다.";
            }
        }

        return "미션 등록에 실패했습니다";
    }

    public void getUserMissionLog(Long userId) {

    }

    private Long getMin(Long p1, Long p2) {
        return p1 > p2 ? p2 : p1;
    }
}
