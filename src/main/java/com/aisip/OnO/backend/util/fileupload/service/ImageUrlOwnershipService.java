package com.aisip.OnO.backend.util.fileupload.service;

import com.aisip.OnO.backend.common.exception.ApplicationException;
import com.aisip.OnO.backend.problem.repository.ProblemImageDataRepository;
import com.aisip.OnO.backend.problemsolve.repository.ProblemSolveImageDataRepository;
import com.aisip.OnO.backend.studyroom.repository.StudyRoomRepository;
import com.aisip.OnO.backend.user.repository.UserRepository;
import com.aisip.OnO.backend.util.fileupload.exception.FileUploadErrorCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 이미지 URL 이 누가 올린 파일인지 판정한다.
 *
 * <p>업로드 key 는 {@code image/users/{userId}/yyyy/MM/dd/{uuid}.{ext}} 형식이라 key 만 보고 올린 사용자를 안다.
 * 이 형식 이전에 올린 파일은 {@code image/yyyy/MM/dd/{uuid}.{ext}} 처럼 사용자 정보가 없다. 이런 파일은
 * 다른 사용자의 행이 쓰고 있지 않을 때만 그 사용자의 것으로 본다.
 *
 * <p>등록과 수정은 {@link #validateUploadedBy} 로 받는 URL 을 거르고, S3 삭제는 {@link #filterDeletable} 로
 * 다른 사용자가 쓰는 파일을 대상에서 뺀다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUrlOwnershipService {

    static final String USER_KEY_PREFIX = "image/users/";

    /** 지금 발급하는 key. 그룹 1 이 올린 사용자 id 다. */
    private static final Pattern USER_KEY = Pattern.compile(
            "^image/users/(\\d+)/\\d{4}/\\d{2}/\\d{2}/[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,5}$");

    /** userId 를 key 에 넣기 전 형식. 이 형식은 다른 사용자의 행이 쓰고 있지 않을 때만 받는다. */
    private static final Pattern DATED_KEY = Pattern.compile(
            "^image/\\d{4}/\\d{2}/\\d{2}/[0-9a-fA-F-]{36}\\.[A-Za-z0-9]{1,5}$");

    private static final int IN_CLAUSE_CHUNK_SIZE = 500;

    private final ProblemImageDataRepository problemImageDataRepository;
    private final ProblemSolveImageDataRepository problemSolveImageDataRepository;
    private final UserRepository userRepository;
    private final StudyRoomRepository studyRoomRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String urlPrefix() {
        return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/";
    }

    public String userKeyPrefix(Long userId) {
        return USER_KEY_PREFIX + userId + "/";
    }

    /**
     * 우리 버킷의 URL 이면 object key 를 돌려준다. 다른 호스트이거나 key 가 비었거나 상위 경로가 섞이면 빈 값이다.
     * 예전에 올린 파일은 key 형식이 제각각이라 형식까지는 보지 않는다.
     */
    public Optional<String> extractObjectKey(String imageUrl) {
        if (imageUrl == null) {
            return Optional.empty();
        }
        String prefix = urlPrefix();
        if (!imageUrl.startsWith(prefix)) {
            return Optional.empty();
        }
        String key = imageUrl.substring(prefix.length());
        if (key.isBlank() || key.startsWith("/") || key.contains("..")) {
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /**
     * 받은 URL 이 모두 이 사용자가 올린 파일인지 확인한다.
     *
     * @param alreadyAttached 수정하는 대상에 이미 붙어 있는 URL. 형식과 상관없이 통과시킨다.
     */
    public void validateUploadedBy(Collection<String> imageUrls, Long userId, Collection<String> alreadyAttached) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        // Set.of() 는 contains(null) 에서 NPE 를 던지므로 HashSet 을 쓴다. null URL 은 아래에서 400 으로 거른다.
        Set<String> attached = alreadyAttached == null ? new HashSet<>() : new HashSet<>(alreadyAttached);

        Set<String> datedUrls = new LinkedHashSet<>();
        for (String imageUrl : imageUrls) {
            if (attached.contains(imageUrl)) {
                continue;
            }
            String key = extractObjectKey(imageUrl)
                    .orElseThrow(() -> new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE));

            Matcher userKey = USER_KEY.matcher(key);
            if (userKey.matches()) {
                if (!userKey.group(1).equals(String.valueOf(userId))) {
                    throw new ApplicationException(FileUploadErrorCase.IMAGE_USER_UNMATCHED);
                }
                continue;
            }
            if (DATED_KEY.matcher(key).matches()) {
                datedUrls.add(imageUrl);
                continue;
            }
            throw new ApplicationException(FileUploadErrorCase.INVALID_IMAGE_FILE);
        }

        if (!findUsedByOtherUsers(datedUrls, userId).isEmpty()) {
            throw new ApplicationException(FileUploadErrorCase.IMAGE_USER_UNMATCHED);
        }
    }

    public void validateUploadedBy(Collection<String> imageUrls, Long userId) {
        validateUploadedBy(imageUrls, userId, List.of());
    }

    public void validateUploadedBy(String imageUrl, Long userId, String alreadyAttached) {
        validateUploadedBy(Collections.singletonList(imageUrl), userId,
                alreadyAttached == null ? List.of() : List.of(alreadyAttached));
    }

    /**
     * ownerUserId 가 지운 행들이 가리키던 URL 중 S3 에서 지워도 되는 것만 돌려준다.
     *
     * <p>지금 형식의 key 는 key 의 사용자가 ownerUserId 일 때만 지운다. 예전 형식 key 는 다른 사용자의 행이
     * 아직 쓰고 있으면 남긴다. 우리 버킷 URL 이 아니면 지우지 않는다.
     */
    public List<String> filterDeletable(Collection<String> imageUrls, Long ownerUserId) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }

        List<String> deletable = new ArrayList<>();
        Set<String> needsReferenceCheck = new LinkedHashSet<>();
        for (String imageUrl : new LinkedHashSet<>(imageUrls)) {
            Optional<String> key = extractObjectKey(imageUrl);
            if (key.isEmpty()) {
                log.warn("S3 delete skipped - reason: not_bucket_url");
                continue;
            }
            Matcher userKey = USER_KEY.matcher(key.get());
            if (userKey.matches()) {
                if (Objects.equals(userKey.group(1), String.valueOf(ownerUserId))) {
                    deletable.add(imageUrl);
                } else {
                    log.warn("S3 delete skipped - reason: other_user_key, ownerUserId: {}", ownerUserId);
                }
                continue;
            }
            needsReferenceCheck.add(imageUrl);
        }

        Set<String> usedByOthers = findUsedByOtherUsers(needsReferenceCheck, ownerUserId);
        for (String imageUrl : needsReferenceCheck) {
            if (usedByOthers.contains(imageUrl)) {
                log.warn("S3 delete skipped - reason: used_by_other_user, ownerUserId: {}", ownerUserId);
                continue;
            }
            deletable.add(imageUrl);
        }
        return deletable;
    }

    public boolean isDeletable(String imageUrl, Long ownerUserId) {
        return !filterDeletable(Collections.singletonList(imageUrl), ownerUserId).isEmpty();
    }

    private Set<String> findUsedByOtherUsers(Collection<String> imageUrls, Long userId) {
        if (imageUrls.isEmpty()) {
            return Set.of();
        }
        Set<String> used = new HashSet<>();
        List<String> urls = new ArrayList<>(imageUrls);
        for (int from = 0; from < urls.size(); from += IN_CLAUSE_CHUNK_SIZE) {
            List<String> chunk = urls.subList(from, Math.min(from + IN_CLAUSE_CHUNK_SIZE, urls.size()));
            used.addAll(problemImageDataRepository.findImageUrlsUsedByOtherUsers(chunk, userId));
            used.addAll(problemSolveImageDataRepository.findImageUrlsUsedByOtherUsers(chunk, userId));
            used.addAll(userRepository.findProfileImageUrlsUsedByOtherUsers(chunk, userId));
            used.addAll(studyRoomRepository.findThumbnailUrlsUsedByOtherUsers(chunk, userId));
        }
        return used;
    }
}
