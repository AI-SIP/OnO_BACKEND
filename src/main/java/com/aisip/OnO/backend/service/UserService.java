package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.repository.UserRepository;
import com.aisip.OnO.backend.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

public interface UserService {

    public User registerOrLoginUser(String email, String name);

    public UserResponseDto getUserById(Long userId);

    public void deleteUserById(Long userId);
}
