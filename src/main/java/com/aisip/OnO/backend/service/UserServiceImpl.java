package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
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
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final ProblemRepository problemRepository;

    @Override
    public User getUserEntity(Long userId){
        Optional<User> optionalUser = userRepository.findById(userId);

        if(optionalUser.isPresent()){
            return optionalUser.get();
        } else{
            throw new UserNotFoundException("유저를 찾을 수 없습니다!");
        }
    }

    @Override
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

    @Override
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

    @Override
    public UserResponseDto getUserDetailsById(Long userId) {
        User user = getUserEntity(userId);
        return UserConverter.convertToResponseDto(user);
    }

    @Override
    public List<UserResponseDto> findAllUsers() {

        List<User> userList = userRepository.findAll();
        return userList.stream().map(UserConverter::convertToResponseDto).collect(Collectors.toList());
    }

    @Override
    public Long findAllProblemCountByUserId(Long userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            return problemRepository.countByUserId(userId);
        } else {
            return 0L;
        }
    }

    @Override
    public List<Long> findAllUsersProblemCount() {
        List<User> userList = userRepository.findAll();
        return userList.stream().map(user -> findAllProblemCountByUserId(user.getId())).collect(Collectors.toList());
    }

    @Override
    public Long findAllUserTypeCountByUserType(UserType userType) {
        return userRepository.countUserByType(userType);
    }

    @Override
    public UserResponseDto updateUser(Long userId, UserRegisterDto userRegisterDto) {

        User user = getUserEntity(userId);
        System.out.println("userId :" + user.getId() + " update");

        if(userRegisterDto.getName() != null){
            user.setName(userRegisterDto.getName());
        }

        if (userRegisterDto.getEmail() != null) {
            user.setEmail(userRegisterDto.getEmail());
        }

        if (userRegisterDto.getIdentifier() != null) {
            user.setIdentifier(userRegisterDto.getIdentifier());
        }

        if (userRegisterDto.getType() != null) {
            user.setType(userRegisterDto.getType());
        }

        User saveUser = userRepository.save(user);

        return UserConverter.convertToResponseDto(saveUser);
    }

    @Override
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
