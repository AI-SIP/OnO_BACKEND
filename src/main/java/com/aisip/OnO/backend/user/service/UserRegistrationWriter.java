package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.practicenote.service.PracticeNoteService;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 회원 계정 한 건의 조회/생성을 <b>각각 독립된 트랜잭션</b>으로 수행한다.
 *
 * <p>{@code user.identifier} 에는 유니크 인덱스가 걸려 있어 "조회 → 없으면 생성"이
 * 한 트랜잭션 안에서는 안전하게 재시도되지 않는다. 삽입이 Duplicate entry 로 실패하는 순간
 * 그 트랜잭션은 롤백 대상이 되고, MySQL REPEATABLE READ 에서는 같은 트랜잭션으로 다시 조회해도
 * 스냅샷 때문에 남이 커밋한 행이 보이지 않는다. 게다가 로그인 진입점인
 * {@code UserAuthService} 자체가 트랜잭션이라 재조회도 그 스냅샷에 갇힌다.
 *
 * <p>그래서 재시도 판단은 {@link UserService} 가 하고, 여기서는 호출마다 새 트랜잭션
 * (= 새 스냅샷)을 연다. 태그 중복 생성 장애에서 쓴 {@code TagWriter} 와 같은 구조다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationWriter {

    private final UserRepository userRepository;
    private final FolderService folderService;
    private final PracticeNoteService practiceNoteService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<User> findByIdentifier(String identifier) {
        return userRepository.findByIdentifier(identifier);
    }

    /**
     * 계정과 초기 데이터(기본 폴더, 기본 복습노트)를 한 트랜잭션으로 만든다.
     *
     * <p>유니크 인덱스에 걸리면 {@link org.springframework.dao.DataIntegrityViolationException}
     * 이 그대로 올라간다. 호출자가 이 트랜잭션 밖에서 재조회로 복구한다.
     * 계정만 남고 초기 데이터가 없는 어중간한 상태가 생기지 않도록 셋을 같은 트랜잭션에 둔다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User create(UserRegisterDto userRegisterDto) {
        User user = userRepository.saveAndFlush(User.from(userRegisterDto));
        folderService.initializeDefaultFoldersIfAbsent(user.getId());
        practiceNoteService.registerDefaultPractice(user.getId());
        return user;
    }
}
