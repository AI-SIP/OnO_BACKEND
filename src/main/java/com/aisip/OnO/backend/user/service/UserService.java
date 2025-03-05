package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.user.UserConverter;
import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.entity.UserType;
import com.aisip.OnO.backend.user.exception.UserNotFoundException;
import com.aisip.OnO.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getUserEntity(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    public UserResponseDto registerGuestUser() {
        User user = new User();
        user.setName(makeGuestName());
        user.setEmail(makeGuestEmail());
        user.setIdentifier(makeGuestIdentifier());
        user.setPlatform("GUEST");
        user.setType(UserType.GUEST);

        User resultUser = userRepository.save(user);
        return UserConverter.convertToResponseDto(resultUser, true);
    }

    public UserResponseDto registerOrLoginUser(UserRegisterDto userRegisterDto, UserType userType) {
        Optional<User> optionalUser = userRepository.findByIdentifier(userRegisterDto.getIdentifier());

        if (optionalUser.isEmpty()) {
            if(userType.equals(UserType.GUEST)){
                userRegisterDto = makeGuestUser();
            }

            User user = new User();
            user.setName(userRegisterDto.getName());
            user.setEmail(userRegisterDto.getEmail());
            user.setIdentifier(userRegisterDto.getIdentifier());
            user.setPlatform(userRegisterDto.getPlatform());
            user.setType(userType);

            User resultUser = userRepository.save(user);
            return UserConverter.convertToResponseDto(resultUser, true);
        }

        return UserConverter.convertToResponseDto(optionalUser.get());
    }

    public UserResponseDto getUserById(Long userId) {

        Optional<User> optionalUser = userRepository.findById(userId);

        if(optionalUser.isPresent()){

            User user = optionalUser.get();
            return UserConverter.convertToResponseDto(user);
        }

        return null;
    }

    public UserResponseDto getUserDetailsById(Long userId) {
        User user = getUserEntity(userId);
        return UserConverter.convertToResponseDto(user);
    }

    public List<UserResponseDto> findAllUsers() {
        List<User> userList = userRepository.findAll();
        return userList.stream().map(UserConverter::convertToResponseDto).collect(Collectors.toList());
    }

    public Long findAllUserTypeCountByUserType(UserType userType) {
        return userRepository.countUserByType(userType);
    }

    public UserResponseDto updateUser(Long userId, UserRegisterDto userRegisterDto) {

        User user = getUserEntity(userId);
        log.info("userId: {} update", user.getId());

        Optional.ofNullable(userRegisterDto.getName()).ifPresent(user::setName);
        Optional.ofNullable(userRegisterDto.getEmail()).ifPresent(user::setEmail);
        Optional.ofNullable(userRegisterDto.getIdentifier()).ifPresent(user::setIdentifier);
        Optional.ofNullable(userRegisterDto.getType()).ifPresent(user::setType);

        return UserConverter.convertToResponseDto(user);
    }

    public void deleteUserById(Long userId) {
        userRepository.deleteById(userId);
    }

    private UserRegisterDto makeGuestUser(){
        String name = makeGuestName();
        String email = makeGuestEmail();
        String identifier = makeGuestIdentifier();

        UserRegisterDto userRegisterDto = new UserRegisterDto();
        userRegisterDto.setName(name);
        userRegisterDto.setEmail(email);
        userRegisterDto.setIdentifier(identifier);
        userRegisterDto.setPlatform("GUEST");

        return userRegisterDto;
    }

    private String makeGuestName() {
        return "Guest" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String makeGuestEmail() {
        return "guest_" + UUID.randomUUID() + "@ono.com";
    }

    private String makeGuestIdentifier() {
        return UUID.randomUUID().toString();
    }
}
