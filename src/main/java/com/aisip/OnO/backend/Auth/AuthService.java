package com.aisip.OnO.backend.Auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserEntityRepository userEntityRepository;

    public UserEntity registerOrLoginUser(String email, String name) {
        UserEntity userEntity = userEntityRepository.findByEmail(email);
        if (userEntity == null) {
            userEntity = new UserEntity();
            userEntity.setEmail(email);
            userEntity.setName(name);
            userEntityRepository.save(userEntity);
        }
        return userEntity;
    }

    public UserEntity getUserById(Long userId) {
        return userEntityRepository.findById(userId).orElse(null);
    }
}
