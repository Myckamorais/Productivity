package com.myck.productivity.auth.controller;

import com.myck.productivity.auth.dto.AuthResponse;
import com.myck.productivity.auth.dto.LoginRequest;
import com.myck.productivity.auth.dto.RegisterRequest;
import com.myck.productivity.auth.service.AuthService;
import com.myck.productivity.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Cadastra um usuário novo e retorna seus dados (201); e-mail repetido retorna 409. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** Autentica com e-mail e senha e retorna o JWT; credenciais erradas retornam 401. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
