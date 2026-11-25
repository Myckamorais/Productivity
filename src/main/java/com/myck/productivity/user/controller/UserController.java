package com.myck.productivity.user.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Retorna os dados do usuário dono do token (id, nome, e-mail e data de criação). */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getById(principal.id());
    }
}
