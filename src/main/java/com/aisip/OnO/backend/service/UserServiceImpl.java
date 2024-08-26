package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

    public User registerOrLoginUser(String email, String name, String identifier, UserType userType) {
        User user = userRepository.findByIdentifier(identifier);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setIdentifier(identifier);
            user.setType(userType);
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

    @Override
    public String makeGuestEmail() {
        return "guest_" + UUID.randomUUID().toString() + "@ono.com";
    }

    @Override
    public String makeGuestName() {
        return "Guest" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public String makeGuestIdentifier() {
        return UUID.randomUUID().toString();
    }
}
