package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.User.User;
import com.aisip.OnO.backend.entity.User.UserType;
import com.aisip.OnO.backend.repository.ProblemRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final ProblemRepository problemRepository;

    public UserResponseDto registerOrLoginUser(String email, String name, String identifier, UserType userType) {
        Optional<User> optionalUser = userRepository.findByIdentifier(identifier);
        if (optionalUser.isEmpty()) {
            User user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setIdentifier(identifier);
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
            LocalDateTime createdAtDateTime = user.getCreatedAt(); // LocalDateTime으로 변경
            LocalDateTime now = LocalDateTime.now();

            // 두 시간 간의 차이를 계산하여 1시간 이내인지 확인
            Duration duration = Duration.between(createdAtDateTime, now);

            if (duration.toHours() < 1) {
                return UserConverter.convertToResponseDto(user, true);
            }

            return UserConverter.convertToResponseDto(user);
        }

        return null;
    }

    @Override
    public UserResponseDto getUserDetailsById(Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        return optionalUser.map(UserConverter::convertToResponseDto).orElse(null);
    }

    @Override
    public List<UserResponseDto> findAllUsers() {

        List<User> userList = userRepository.findAll();
        return userList.stream().map(UserConverter::convertToResponseDto).collect(Collectors.toList());
    }

    @Override
    public Long findAllProblemCountByUserId(Long userId) {
        // 유저를 찾아서 해당 유저가 있는지 확인
        Optional<User> user = userRepository.findById(userId);
        if (user.isPresent()) {
            return problemRepository.countByUserId(userId);
        } else {
            return 0L;
        }
    }

    @Override
    public List<Long> findAllUsersProblemCount() {
        // 유저를 찾아서 해당 유저가 있는지 확인
        List<User> userList = userRepository.findAll();
        return userList.stream().map(user -> {
            return findAllProblemCountByUserId(user.getId());
        }).collect(Collectors.toList());
    }

    @Override
    public Long findAllUserTypeCountByUserType(UserType userType) {
        return userRepository.countUserByType(userType);
    }

    @Override
    public UserResponseDto updateUser(Long userId, UserRegisterDto userRegisterDto) {

        Optional<User> optionalUser = userRepository.findById(userId);

        if (optionalUser.isPresent()) {

            User user = optionalUser.get();
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
        } else {
            return null;
        }
    }

    public List<User> getAllUsers() {
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
