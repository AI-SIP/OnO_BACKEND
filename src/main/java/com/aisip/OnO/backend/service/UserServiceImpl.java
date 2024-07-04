package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.User;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;

    @Override
    public UserResponseDto getUserByUserId(Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            return UserConverter.convertToResponseDto(user);
        } else {
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
    }

    @Override
    public UserResponseDto saveUser(UserRegisterDto userRegisterDto) {
        String googleId = userRegisterDto.getGoogleId();
        Optional<User> optionalUser = userRepository.findByGoogleId(googleId);
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            return UserConverter.convertToResponseDto(user);
        }

        User user = User.builder()
                .googleId(userRegisterDto.getGoogleId())
                .email(userRegisterDto.getEmail())
                .userName(userRegisterDto.getUserName())
                .createdAt(LocalDate.now())
                .updateAt(LocalDate.now())
                .build();

        User savedUser = userRepository.save(user);
        return UserConverter.convertToResponseDto(savedUser);
    }

    @Override
    public UserResponseDto getUserByGoogleId(String googleId) {
        Optional<User> optionalUser = userRepository.findByGoogleId(googleId);
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            return UserConverter.convertToResponseDto(user);
        } else {
            throw new UserNotFoundException("User not found with Google ID: " + googleId);
        }
    }
}