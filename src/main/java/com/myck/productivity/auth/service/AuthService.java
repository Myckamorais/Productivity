package com.myck.productivity.auth.service;

import com.myck.productivity.auth.dto.AuthResponse;
import com.myck.productivity.auth.dto.LoginRequest;
import com.myck.productivity.auth.dto.RegisterRequest;
import com.myck.productivity.common.BusinessException;
import com.myck.productivity.user.dto.UserResponse;
import com.myck.productivity.user.entity.User;
import com.myck.productivity.user.mapper.UserMapper;
import com.myck.productivity.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final String BEARER = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    /** Cria o usuário: e-mail normalizado e único (409 se já existir) e senha guardada só como hash BCrypt. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailAlreadyRegistered();
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        try {
            // saveAndFlush força o INSERT agora; se duas requisições simultâneas passaram pelo
            // existsByEmail, o índice único do banco barra a segunda e ela também recebe 409
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailAlreadyRegistered();
        }
        return userMapper.toResponse(user);
    }

    /**
     * Confere e-mail e senha e emite o token. E-mail inexistente e senha errada dão a mesma
     * resposta (401), para ninguém descobrir quais e-mails estão cadastrados.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos."));
        return new AuthResponse(jwtService.generateToken(user), BEARER, jwtService.expiresInSeconds());
    }

    // Minúsculas e sem espaços nas pontas, para o mesmo e-mail digitado de formas diferentes ser um só
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException emailAlreadyRegistered() {
        return new BusinessException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
    }
}
