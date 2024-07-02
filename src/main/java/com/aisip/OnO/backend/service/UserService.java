package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User;

public interface UserService {

    public UserResponseDto getUserByUserId(Long userId);
    public UserResponseDto saveUser(UserRegisterDto userRegisterDto);
    public UserResponseDto getUserByGoogleId(String googleId);


}

