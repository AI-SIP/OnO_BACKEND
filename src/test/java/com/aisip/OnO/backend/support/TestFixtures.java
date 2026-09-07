package com.aisip.OnO.backend.support;

import com.aisip.OnO.backend.folder.dto.FolderRegisterDto;
import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.repository.FolderRepository;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 모든 도메인이 공통으로 필요로 하는 최소 픽스처.
 *
 * <p>도메인 고유 픽스처(문제, 복습노트, 스터디룸 등)는 각 도메인 테스트 패키지에서
 * 별도 픽스처 클래스로 정의한다. 여기에는 사용자·폴더처럼 어느 도메인에서나
 * 전제가 되는 것만 둔다.
 *
 * <p>{@code User.identifier}에는 유니크 인덱스가 걸려 있으므로 호출마다 다른 값을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class TestFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final UserRepository userRepository;
    private final FolderRepository folderRepository;

    public User createUser() {
        return createUser("member");
    }

    public User createUser(String namePrefix) {
        long seq = SEQUENCE.incrementAndGet();
        return userRepository.save(User.from(UserRegisterDto.builder()
                .email(namePrefix + seq + "@test.ono")
                .name(namePrefix + seq)
                .identifier("test-identifier-" + seq)
                .platform("google")
                .build()));
    }

    /** 소유권 검증 테스트용. 서로 다른 두 사용자를 만든다. */
    public User createOtherUser() {
        return createUser("other");
    }

    public Folder createRootFolder(Long userId) {
        return createFolder(userId, "루트 폴더", null);
    }

    public Folder createFolder(Long userId, String name, Folder parentFolder) {
        FolderRegisterDto registerDto = new FolderRegisterDto(
                name,
                null,
                parentFolder == null ? null : parentFolder.getId()
        );
        Folder folder = parentFolder == null
                ? Folder.from(registerDto, userId)
                : Folder.from(registerDto, parentFolder, userId);
        return folderRepository.save(folder);
    }
}
