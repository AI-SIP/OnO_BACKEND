package com.aisip.OnO.backend.repository;

import com.aisip.OnO.backend.entity.SocialLogin.SocialLogin;
import com.aisip.OnO.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialLoginRepository extends JpaRepository<SocialLogin, Long> {
    Optional<SocialLogin> findBySocialId(String socialId);

    Optional<SocialLogin> findByUser(User user);
}
