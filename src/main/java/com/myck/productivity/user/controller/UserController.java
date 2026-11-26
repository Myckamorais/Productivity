package com.myck.productivity.user.controller;

import com.myck.productivity.auth.security.AuthenticatedUser;
import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.dto.UserSettingsRequest;
import com.myck.productivity.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Retorna os dados do usuário dono do token, incluindo fuso e meta diária de foco. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getById(principal.id());
    }

    /** Atualiza o fuso e a meta diária de foco do usuário logado e retorna os dados atualizados. */
    @PutMapping("/me/settings")
    public UserResponse updateSettings(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UserSettingsRequest request) {
        return userService.updateSettings(principal.id(), request);
    }
}
