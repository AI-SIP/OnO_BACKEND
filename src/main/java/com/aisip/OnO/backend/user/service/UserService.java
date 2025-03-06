package com.aisip.OnO.backend.user.service;

import com.aisip.OnO.backend.user.dto.UserRegisterDto;
import com.aisip.OnO.backend.user.dto.UserResponseDto;
import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.entity.UserType;
import com.aisip.OnO.backend.user.exception.UserErrorCase;
import com.aisip.OnO.backend.common.exception.ApplicationException;
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

    private User findUserEntity(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApplicationException(UserErrorCase.USER_NOT_FOUND));
    }

    private User createGuestUser() {
        UserRegisterDto userRegisterDto = new UserRegisterDto(
                makeGuestName(),
                makeGuestEmail(),
                makeGuestIdentifier(),
                "GUEST",
                UserType.GUEST
        );

        return User.from(userRegisterDto);
    }

    private Long registerGuestUser() {

        User user = createGuestUser();
        userRepository.save(user);

        return user.getId();
    }

    private Long registerMemberUser(UserRegisterDto userRegisterDto) {

        User user = User.from(userRegisterDto);
        userRepository.save(user);

        return user.getId();
    }

    private Long registerUser(UserRegisterDto userRegisterDto) {
        if (userRegisterDto.userType().equals(UserType.MEMBER)) {
            return registerMemberUser(userRegisterDto);
        } else{
            return registerGuestUser();
        }
    }

    public Long loginUser(UserRegisterDto userRegisterDto) {
        Optional<User> optionalUser = userRepository.findByIdentifier(userRegisterDto.identifier());

        if (optionalUser.isPresent()) {
            return optionalUser.get().getId();
        } else{
            return registerUser(userRegisterDto);
        }
    }

    public UserResponseDto findUser(Long userId) {
        User user = findUserEntity(userId);
        return UserResponseDto.from(user);
    }

    public List<UserResponseDto> findAllUsers() {
        List<User> userList = userRepository.findAll();
        return userList.stream().map(UserResponseDto::from).collect(Collectors.toList());
    }

    public Long findAllUserTypeCountByUserType(UserType userType) {
        return userRepository.countUserByType(userType);
    }

    public void updateUser(Long userId, UserRegisterDto userRegisterDto) {

        User user = findUserEntity(userId);
        user.updateUser(userRegisterDto);

        log.info("userId: {} update", user.getId());
    }

    public void deleteUserById(Long userId) {
        userRepository.deleteById(userId);
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
