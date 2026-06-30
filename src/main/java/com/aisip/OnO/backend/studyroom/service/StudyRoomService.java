package com.aisip.OnO.backend.studyroom.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomDtos.*;
import com.aisip.OnO.backend.studyroom.dto.StudyRoomStats;
import com.aisip.OnO.backend.studyroom.entity.StudyRoom;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import com.aisip.OnO.backend.studyroom.exception.StudyRoomErrorCase;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomMemberRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.config.rabbitmq.producer.S3DeleteProducer;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudyRoomService {

    public static final int MAX_ROOM_MEMBER_COUNT = 20;
    public static final int MAX_USER_ROOM_COUNT = 10;
    private static final long MAX_THUMBNAIL_SIZE_BYTES = 5 * 1024 * 1024;

    private final StudyRoomRepository roomRepository;
    private final StudyRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StudyRoomAccessService accessService;
    private final StudyRoomStatsService statsService;
    private final StudyRoomMapper mapper;
    private final FileUploadService fileUploadService;
    private final S3DeleteProducer s3DeleteProducer;

    @Transactional(readOnly = true)
    public List<StudyRoomListResponse> getMyRooms(Long userId) {
        List<StudyRoomMember> memberships = memberRepository.findAllWithRoomByUserId(userId);
        List<Long> roomIds = memberships.stream().map(member -> member.getRoom().getId()).toList();
        if (roomIds.isEmpty()) {
            return List.of();
        }
        List<StudyRoomMember> allRoomMembers = memberRepository.findAllWithRoomAndUserByRoomIds(roomIds);
        Map<Long, List<StudyRoomMember>> membersByRoomId = allRoomMembers.stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m.getRoom().getId()));
        List<Long> allUserIds = allRoomMembers.stream().map(m -> m.getUser().getId()).distinct().toList();
        Map<Long, Integer> todayPracticeCountByUserId = statsService.getTodayPracticeCounts(allUserIds);
        return memberships.stream()
                .map(member -> mapper.toListResponse(
                        member,
                        membersByRoomId.getOrDefault(member.getRoom().getId(), List.of()),
                        todayPracticeCountByUserId
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public StudyRoomDetailResponse getRoom(Long roomId, Long userId) {
        accessService.validateMember(roomId, userId);
        StudyRoom room = accessService.getRoomOrThrow(roomId);
        return buildDetail(room);
    }

    @Transactional
    public StudyRoomDetailResponse createRoom(StudyRoomCreateRequest request, Long userId) {
        return createRoom(request == null ? null : request.name(), null, userId);
    }

    @Transactional
    public StudyRoomDetailResponse createRoom(String name, MultipartFile thumbnailImage, Long userId) {
        String validatedName = validateName(name);
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
        if (memberRepository.countByUserId(userId) >= MAX_USER_ROOM_COUNT) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_LIMIT_EXCEEDED);
        }
        StudyRoom room = StudyRoom.create(validatedName, userId);
        room.addMember(StudyRoomMember.create(user, StudyRoomMemberRole.HOST));
        roomRepository.save(room);
        if (thumbnailImage != null && !thumbnailImage.isEmpty()) {
            validateThumbnail(thumbnailImage);
            String thumbnailUrl = fileUploadService.uploadFileToS3(thumbnailImage);
            deleteThumbnailOnRollback(thumbnailUrl, room.getId());
            room.updateThumbnailUrl(thumbnailUrl);
        }
        return buildDetail(room);
    }

    @Transactional
    public StudyRoomDetailResponse updateRoom(Long roomId, Long userId, StudyRoomUpdateRequest request) {
        String name = validateName(request == null ? null : request.name());
        accessService.validateHost(roomId, userId);
        StudyRoom room = lockRoom(roomId);
        room.updateName(name);
        return buildDetail(room);
    }

    @Transactional
    public StudyRoomDetailResponse updateRoom(Long roomId, Long userId, String name, MultipartFile thumbnailImage) {
        if ((name == null || name.isBlank()) && (thumbnailImage == null || thumbnailImage.isEmpty())) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
        accessService.validateHost(roomId, userId);
        StudyRoom room = lockRoom(roomId);
        if (name != null) {
            room.updateName(validateName(name));
        }
        if (thumbnailImage != null) {
            validateThumbnail(thumbnailImage);
            String previousThumbnailUrl = room.getThumbnailUrl();
            String thumbnailUrl = fileUploadService.uploadFileToS3(thumbnailImage);
            deleteThumbnailOnRollback(thumbnailUrl, roomId);
            room.updateThumbnailUrl(thumbnailUrl);
            deleteThumbnailAsync(previousThumbnailUrl, roomId);
        }
        return buildDetail(room);
    }

    @Transactional
    public void deleteRoom(Long roomId, Long userId) {
        accessService.validateHost(roomId, userId);
        StudyRoom room = lockRoom(roomId);
        deleteThumbnailAsync(room.getThumbnailUrl(), roomId);
        roomRepository.delete(room);
    }

    @Transactional
    public StudyRoom lockRoom(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, userId);
        if (member.getRole() != StudyRoomMemberRole.HOST) {
            memberRepository.deleteByRoomIdAndUserId(roomId, userId);
            return;
        }

        StudyRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_NOT_FOUND));
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(roomId);
        StudyRoomMember nextHost = members.stream()
                .filter(candidate -> !candidate.getUser().getId().equals(userId))
                .findFirst()
                .orElse(null);
        if (nextHost == null) {
            deleteThumbnailAsync(room.getThumbnailUrl(), roomId);
            roomRepository.delete(room);
            return;
        }
        nextHost.promoteToHost();
        room.updateHostUserId(nextHost.getUser().getId());
        memberRepository.deleteByRoomIdAndUserId(roomId, userId);
    }

    @Transactional
    public void kickMember(Long roomId, Long memberUserId, Long hostUserId) {
        accessService.validateHost(roomId, hostUserId);
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, memberUserId);
        if (member.getRole() == StudyRoomMemberRole.HOST) {
            throw new ApplicationException(StudyRoomErrorCase.STUDY_ROOM_HOST_ONLY);
        }
        memberRepository.deleteByRoomIdAndUserId(roomId, memberUserId);
    }

    @Transactional
    public GoalUpdateResponse updateGoal(Long roomId, Long userId, StudyRoomGoalUpdateRequest request) {
        StudyRoomMember member = accessService.getMemberOrThrow(roomId, userId);
        Integer weeklyGoal = request.weeklyGoal();
        if (weeklyGoal != null && weeklyGoal < 0) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
        member.updateWeeklyGoal(weeklyGoal == null || weeklyGoal == 0 ? null : weeklyGoal);
        StudyRoomStats stats = statsService.getWeeklyStats(List.of(userId)).get(userId);
        return new GoalUpdateResponse(member.getWeeklyGoal(), member.getWeeklyGoal() == null ? null : stats.weeklyProblemCount());
    }

    @Transactional
    public StudyRoomThumbnailUpdateResponse updateThumbnail(Long roomId, Long userId, MultipartFile thumbnail) {
        accessService.validateHost(roomId, userId);
        validateThumbnail(thumbnail);
        StudyRoom room = accessService.getRoomOrThrow(roomId);
        String previousThumbnailUrl = room.getThumbnailUrl();
        String thumbnailUrl = fileUploadService.uploadFileToS3(thumbnail);
        deleteThumbnailOnRollback(thumbnailUrl, roomId);
        room.updateThumbnailUrl(thumbnailUrl);
        deleteThumbnailAsync(previousThumbnailUrl, roomId);
        return new StudyRoomThumbnailUpdateResponse(thumbnailUrl);
    }

    @Transactional
    public StudyRoomThumbnailUpdateResponse updateThumbnailByUrl(Long roomId, Long userId, String thumbnailUrl) {
        accessService.validateHost(roomId, userId);
        fileUploadService.validateS3Url(thumbnailUrl);
        StudyRoom room = accessService.getRoomOrThrow(roomId);
        String previousThumbnailUrl = room.getThumbnailUrl();
        room.updateThumbnailUrl(thumbnailUrl);
        deleteThumbnailAsync(previousThumbnailUrl, roomId);
        return new StudyRoomThumbnailUpdateResponse(thumbnailUrl);
    }

    StudyRoomDetailResponse buildDetail(StudyRoom room) {
        List<StudyRoomMember> members = memberRepository.findAllWithUserByRoomId(room.getId());
        List<Long> userIds = members.stream().map(member -> member.getUser().getId()).toList();
        Map<Long, StudyRoomStats> stats = statsService.getWeeklyStats(userIds);
        Map<Long, Integer> todayPracticeCounts = statsService.getTodayPracticeCounts(userIds);
        return mapper.toDetailResponse(room, members, stats, todayPracticeCounts);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 20) {
            throw new ApplicationException(StudyRoomErrorCase.INVALID_STUDY_ROOM_REQUEST);
        }
        return name.trim();
    }

    private void validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()) {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }
        if (thumbnail.getSize() > MAX_THUMBNAIL_SIZE_BYTES) {
            throw new ApplicationException(FileUploadErrorCase.FILE_SIZE_EXCEEDED);
        }

        String extension = getLowercaseExtension(thumbnail.getOriginalFilename());
        String contentType = normalizeContentType(thumbnail.getContentType(), extension);
        if (!isAllowedImageType(contentType, extension) || !hasAllowedImageSignature(thumbnail, contentType)) {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }
    }

    private String getLowercaseExtension(String originalFilename) {
        if (originalFilename == null) {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }
        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType, String extension) {
        if (contentType == null || "application/octet-stream".equals(contentType)) {
            return switch (extension) {
                case "jpg", "jpeg" -> "image/jpeg";
                case "png" -> "image/png";
                case "webp" -> "image/webp";
                default -> contentType != null ? contentType : "";
            };
        }
        return contentType;
    }

    private boolean isAllowedImageType(String contentType, String extension) {
        if (contentType == null) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
    }

    private boolean hasAllowedImageSignature(MultipartFile thumbnail, String contentType) {
        byte[] header = new byte[12];
        int read;
        try {
            read = thumbnail.getInputStream().read(header);
        } catch (IOException e) {
            throw new ApplicationException(FileUploadErrorCase.FILE_UPLOAD_FAILED);
        }

        return switch (contentType) {
            case "image/jpeg", "image/jpg" -> read >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
            case "image/png" -> read >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;
            case "image/webp" -> read >= 12
                    && header[0] == 0x52
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x46
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50;
            default -> false;
        };
    }

    private void deleteThumbnailAsync(String thumbnailUrl, Long roomId) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            s3DeleteProducer.sendDeleteMessage(thumbnailUrl, roomId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                s3DeleteProducer.sendDeleteMessage(thumbnailUrl, roomId);
            }
        });
    }

    private void deleteThumbnailOnRollback(String thumbnailUrl, Long roomId) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    s3DeleteProducer.sendDeleteMessage(thumbnailUrl, roomId);
                }
            }
        });
    }
}
