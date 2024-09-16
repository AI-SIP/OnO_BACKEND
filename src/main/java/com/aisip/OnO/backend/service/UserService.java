package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;

import java.util.List;

public interface UserService {

    User registerOrLoginUser(String email, String name, String identifier, UserType userType);

    UserResponseDto getUserById(Long userId);

    User getUserDetailsById(Long userId);

    List<User> findAllUsers();

    Long findAllProblemCountByUserId(Long userId);

    UserResponseDto updateUser(Long userId, UserRegisterDto userRegisterDto);

    void deleteUserById(Long userId);

    String makeGuestEmail();

    String makeGuestName();

    String makeGuestIdentifier();
}
