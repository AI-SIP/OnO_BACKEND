package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.repository.UserRepository;
import com.aisip.OnO.backend.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    public User registerOrLoginUser(String email, String name) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            userRepository.save(user);
        }
        return user;
    }

    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }
}
