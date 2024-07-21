package com.aisip.OnO.backend.service;

import com.aisip.OnO.backend.Dto.User.UserRegisterDto;
import com.aisip.OnO.backend.Dto.User.UserResponseDto;
import com.aisip.OnO.backend.converter.UserConverter;
import com.aisip.OnO.backend.entity.SocialLogin.SocialLogin;
import com.aisip.OnO.backend.entity.SocialLogin.SocialLoginType;
import com.aisip.OnO.backend.entity.User;
import com.aisip.OnO.backend.exception.UserNotFoundException;
import com.aisip.OnO.backend.repository.SocialLoginRepository;
import com.aisip.OnO.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;

    private final SocialLoginRepository socialLoginRepository;

    @Override
    public UserResponseDto getUserByUserId(Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);
        if(optionalUser.isPresent()){
            User user = optionalUser.get();
            SocialLogin socialLogin = socialLoginRepository.findByUser(user).get();
            return UserConverter.convertToResponseDto(user, socialLogin);
        } else {
            throw new UserNotFoundException("User not found with ID: " + userId);
        }
    }

    @Override
    public UserResponseDto saveUser(UserRegisterDto userRegisterDto) {

        Optional<SocialLogin> optionalSocialLogin;

        User user;
        SocialLogin socialLogin;
        optionalSocialLogin = socialLoginRepository.findBySocialId(userRegisterDto.getSocialId());
        if(optionalSocialLogin.isPresent()){
            socialLogin = optionalSocialLogin.get();
            user = socialLogin.getUser();
        }

        else {
            // 새로운 사용자 등록
            user = User.builder()
                    .email(userRegisterDto.getEmail())
                    .userName(userRegisterDto.getUserName())
                    .createdAt(LocalDate.now())
                    .updateAt(LocalDate.now())
                    .build();

            user = userRepository.save(user); // 새 사용자 정보 저장

            // 새 소셜 로그인 정보 저장
            socialLogin = SocialLogin.builder()
                    .socialId(userRegisterDto.getSocialId())
                    .socialLoginType(SocialLoginType.valueOf(userRegisterDto.getSocialLoginType()))
                    .linkedDate(LocalDate.now())
                    .user(user)
                    .build();

            socialLoginRepository.save(socialLogin); // 소셜 로그인 정보 저장
        }

        return UserConverter.convertToResponseDto(user, socialLogin);
    }
}