package com.aisip.OnO.backend.Auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserEntityRepository userEntityRepository;

    public UserEntity registerOrLoginUser(GoogleIdToken.Payload payload) {
        String userId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");

        UserEntity userEntity = userEntityRepository.findByEmail(email);
        if (userEntity == null) {
            userEntity = new UserEntity();
            userEntity.setUserId(userId);
            userEntity.setEmail(email);
            userEntity.setName(name);
            userEntityRepository.save(userEntity);
        }

        return userEntity;
    }
}
