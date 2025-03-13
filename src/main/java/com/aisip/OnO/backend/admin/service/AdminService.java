package com.aisip.OnO.backend.admin.service;

import com.aisip.OnO.backend.fileupload.service.FileUploadService;
import com.aisip.OnO.backend.folder.service.FolderService;
import com.aisip.OnO.backend.problem.service.ProblemService;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
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

    private final UserService userService;
    private final ProblemService problemService;

    private final FolderService folderService;

    private final FileUploadService fileUploadService;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userService.findUserEntityByIdentifier(identifier);

        return new CustomAdminService(
                user.getId(), // userId를 포함시킵니다.
                user.getIdentifier(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ADMIN"))
        );
    }
}
