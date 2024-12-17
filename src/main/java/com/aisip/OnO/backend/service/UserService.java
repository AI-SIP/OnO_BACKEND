package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;

import java.util.List;

public interface UserService {

    User getUserEntity(Long userId);

    UserResponseDto registerGuestUser();

    UserResponseDto registerOrLoginUser(UserRegisterDto userRegisterDto, UserType userType);

    UserResponseDto getUserById(Long userId);

    UserResponseDto getUserDetailsById(Long userId);

    List<UserResponseDto> findAllUsers();

    Long findAllProblemCountByUserId(Long userId);

    List<Long> findAllUsersProblemCount();

    Long findAllUserTypeCountByUserType(UserType userType);

    UserResponseDto updateUser(Long userId, UserRegisterDto userRegisterDto);

    void deleteUserById(Long userId);
}
