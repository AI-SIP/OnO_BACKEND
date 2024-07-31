package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.User;
import com.aisip.OnO.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService{
    @Autowired
    private UserRepository userRepository;

    public User registerOrLoginUser(String email, String name, String identifier) {
        User user = userRepository.findByIdentifier(identifier);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setIdentifier(identifier);
            userRepository.save(user);
        }

        return user;
    }

    public UserResponseDto getUserById(Long userId) {

        User user = userRepository.findById(userId).orElse(null);

        return UserConverter.convertToResponseDto(user);
    }

    @Override
    public void deleteUserById(Long userId) {
        userRepository.deleteById(userId);
    }
}
