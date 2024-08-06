package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;

public interface UserService {

    public User registerOrLoginUser(String email, String name, String identifier, UserType userType);

    public UserResponseDto getUserById(Long userId);

    public void deleteUserById(Long userId);


    public String makeGuestEmail();

    public String makeGuestName();

    public String makeGuestIdentifier();
}
