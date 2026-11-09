package com.aisip.OnO.backend.admin.service;

import com.aisip.OnO.backend.util.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ProblemService problemService;

    private final FolderService folderService;

    private final FileUploadService fileUploadService;

    @Value("${admin.identifier}")
    private String adminIdentifier;

    /**
     * 관리자 폼 로그인에서 계정을 불러온다.
     *
     * <p>{@code AdminConfig} 가 만드는 관리자 계정, 즉 설정된 {@code admin.identifier} 와 같은
     * identifier 를 가진 계정만 받는다. 그 밖의 identifier 는 사용자가 있든 없든 같은 예외로 거절한다.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (!isAdminIdentifier(identifier)) {
            throw new UsernameNotFoundException("관리자 계정이 아닙니다.");
        }

        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("관리자 계정이 없습니다."));

        return new CustomAdminService(
                user.getId(), // userId를 포함시킵니다.
                user.getIdentifier(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private boolean isAdminIdentifier(String identifier) {
        return adminIdentifier != null
                && !adminIdentifier.isBlank()
                && adminIdentifier.equals(identifier);
    }
}
