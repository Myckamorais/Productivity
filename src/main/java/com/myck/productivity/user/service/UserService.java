package com.myck.productivity.user.service;

import com.myck.productivity.common.ResourceNotFoundException;
import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.dto.UserSettingsRequest;
import com.myck.productivity.user.entity.User;
import com.myck.productivity.user.mapper.UserMapper;
import com.myck.productivity.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    /**
     * Devolve o usuário logado. O id vem do token já validado, nunca da requisição;
     * se a conta foi apagada depois da emissão do token, responde 404.
     */
    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    /** Substitui o fuso e a meta diária de foco do usuário logado; meta omitida remove a meta. */
    @Transactional
    public UserResponse updateSettings(Long userId, UserSettingsRequest request) {
        User user = findUser(userId);
        user.setTimeZone(request.timeZone());
        user.setDailyFocusGoalMinutes(request.dailyFocusGoalMinutes());
        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }
}
