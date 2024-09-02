package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

    public User registerOrLoginUser(String email, String name, String identifier, UserType userType) {
        Optional<User> optionalUser = userRepository.findByIdentifier(identifier);
        if (optionalUser.isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setIdentifier(identifier);
            user.setType(userType);
            userRepository.save(user);
            return user;
        }

        return optionalUser.get();
    }

    public UserResponseDto getUserById(Long userId) {

        User user = userRepository.findById(userId).orElse(null);

        return UserConverter.convertToResponseDto(user);
    }

    @Override
    public User getUserDetailsById(Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        return optionalUser.orElse(null);
    }

    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User updateUser(Long userId, UserRegisterDto userRegisterDto) {

        Optional<User> optionalUser = userRepository.findById(userId);
        System.out.println("user update");

        if(optionalUser.isPresent()){
            User user = optionalUser.get();

            user.setName(userRegisterDto.getName());
            user.setEmail(userRegisterDto.getEmail());
            user.setIdentifier(userRegisterDto.getIdentifier());
            user.setType(userRegisterDto.getType());

            return userRepository.save(user);
        } else{
            return null;
        }
    }

    public List<User> getAllUsers(){
        return null;
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
