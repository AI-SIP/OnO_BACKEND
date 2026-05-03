package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.admin.dto.AdminUserResponseDto;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.webhook.DiscordWebhookNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final FolderService folderService;

    private final ProblemService problemService;

    private final PracticeNoteService practiceNoteService;

    private final DiscordWebhookNotificationService discordWebhookNotificationService;

    private User findUserEntity(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
    }

    @Transactional
    public User findUserEntityByIdentifier(String identifier){
        return userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
    }

    private User createGuestUser() {
        UserRegisterDto userRegisterDto = new UserRegisterDto(
                makeGuestEmail(),
                makeGuestName(),
                makeGuestIdentifier(),
                "GUEST",
                null
        );

        discordWebhookNotificationService.sendMessage("새로운 게스트 유저가 가입했습니다!", "");
        return User.from(userRegisterDto);
    }

    @Transactional
    public UserResponseDto registerGuestUser() {

        User user = createGuestUser();
        userRepository.save(user);
        folderService.initializeDefaultFoldersIfAbsent(user.getId());
        practiceNoteService.registerDefaultPractice(user.getId());

        return UserResponseDto.from(user);
    }

    @Transactional
    public UserResponseDto registerMemberUser(UserRegisterDto userRegisterDto) {
        return userRepository.findByIdentifier(userRegisterDto.identifier())
                .map(UserResponseDto::from)
                .orElseGet(() -> {
                    User user = User.from(userRegisterDto);
                    userRepository.save(user);
                    folderService.initializeDefaultFoldersIfAbsent(user.getId());
                    practiceNoteService.registerDefaultPractice(user.getId());
                    discordWebhookNotificationService.sendMessage("새로운 멤버 유저가 가입했습니다!", "Username: "  + userRegisterDto.name());
                    return UserResponseDto.from(user);
                });
    }

    @Transactional(readOnly = true)
    public UserResponseDto findUser(Long userId) {
        User user = findUserEntity(userId);
        return UserResponseDto.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> findAllUsers() {
        List<User> userList = userRepository.findAll();
        return userList.stream()
                .sorted((u1, u2) -> u2.getCreatedAt().compareTo(u1.getCreatedAt())) // 최신순 정렬
                .map(UserResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponseDto> findAdminUsers(int page, int size, String sortBy, String direction) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return userRepository.findAdminUsers(pageRequest, sortBy, direction)
                .map(row -> AdminUserResponseDto.from(row.user(), row.problemCount()));
    }

    @Transactional(readOnly = true)
    public long countAllUsers() {
        return userRepository.count();
    }

    @Transactional
    public void touchLastActiveAt(Long userId) {
        findUserEntity(userId).touchLastActiveAt();
    }

    @Transactional
    public void updateUser(Long userId, UserRegisterDto userRegisterDto) {

        User user = findUserEntity(userId);
        user.updateUser(userRegisterDto);

        log.info("userId: {} has updated", userId);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUserById(Long userId) {
        practiceNoteService.deleteAllPracticesByUser(userId);
        problemService.deleteAllUserProblems(userId);
        folderService.deleteAllUserFolders(userId);

        userRepository.deleteById(userId);
        userRepository.flush();

        log.info("userId: {} has deleted", userId);
    }

    private String makeGuestName() {
        return "Guest" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String makeGuestEmail() {
        return "guest_" + UUID.randomUUID() + "@ono.com";
    }

    private String makeGuestIdentifier() {
        return UUID.randomUUID().toString();
    }

    @Transactional
    public void updateUserLevel(Long userId, String levelType, Long levelValue, Long pointValue) {
        User user = findUserEntity(userId);

        switch (levelType) {
            case "attendance" -> user.getUserMissionStatus().setAttendanceLevel(levelValue, pointValue);
            case "noteWrite" -> user.getUserMissionStatus().setNoteWriteLevel(levelValue, pointValue);
            case "problemPractice" -> user.getUserMissionStatus().setProblemPracticeLevel(levelValue, pointValue);
            case "notePractice" -> user.getUserMissionStatus().setNotePracticeLevel(levelValue, pointValue);
            case "totalStudy" -> user.getUserMissionStatus().setTotalStudyLevel(levelValue, pointValue);
            default -> throw new ApplicationException(UserErrorCase.USER_NOT_FOUND);
        }

        userRepository.save(user);
        log.info("userId: {} level {} updated to level: {}, point: {}", userId, levelType, levelValue, pointValue);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyNewUsersCount(int days) {
        LocalDate today = LocalDate.now();
        return getDailyNewUsersCount(today.minusDays(days - 1L), today);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getDailyNewUsersCount(LocalDate startDate, LocalDate endDate) {
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        userRepository.countDailyNewUsers(startDate.atStartOfDay(), endDate.atTime(LocalTime.MAX))
                .forEach(row -> result.put(toLocalDate(row[0]), (Long) row[1]));

        Map<LocalDate, Long> orderedResult = new LinkedHashMap<>();
        for (LocalDate date = endDate; !date.isBefore(startDate); date = date.minusDays(1)) {
            orderedResult.put(date, result.getOrDefault(date, 0L));
        }

        return orderedResult;
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getUsersByDate(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        return userRepository.findAllByCreatedAtBetweenOrderByCreatedAtDesc(startOfDay, endOfDay).stream()
                .map(UserResponseDto::from)
                .collect(Collectors.toList());
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
