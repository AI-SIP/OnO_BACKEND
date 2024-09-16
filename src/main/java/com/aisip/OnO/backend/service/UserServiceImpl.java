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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    private final ProblemRepository problemRepository;

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
