package com.myck.productivity.user.mapper;

import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.entity.User;
import org.springframework.stereotype.Component;

/** Converte a entidade em DTO: a entidade nunca sai pela API. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
    }
}
