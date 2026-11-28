package com.myck.productivity.auth.service;

import com.myck.productivity.auth.dto.AuthResponse;
import com.myck.productivity.auth.dto.LoginRequest;
import com.myck.productivity.auth.dto.RegisterRequest;
import com.myck.productivity.auth.security.AuthRateLimiter;
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
import org.springframework.util.function.SingletonSupplier;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String BEARER = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuthRateLimiter rateLimiter;
    // Hash BCrypt de uma senha aleatória, com o mesmo custo dos hashes reais. Gerado só na primeira vez que
    // for preciso (gerar custa o mesmo que conferir; não vale atrasar a subida da aplicação por isso)
    private final SingletonSupplier<String> dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService, UserMapper userMapper, AuthRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.rateLimiter = rateLimiter;
        this.dummyHash = SingletonSupplier.of(() -> passwordEncoder.encode(UUID.randomUUID().toString()));
    }

    /**
     * Cria o usuário: e-mail normalizado e único (409 se já existir), senha guardada só como hash BCrypt
     * e fuso informado (ou o padrão America/Sao_Paulo).
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailAlreadyRegistered();
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        if (request.timeZone() != null) {
            user.setTimeZone(request.timeZone());
        }
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
     * Depois de várias senhas erradas, o login daquele e-mail fica bloqueado por um tempo (429).
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        rateLimiter.checkLoginAllowed(email);
        Optional<User> user = userRepository.findByEmail(email);
        // A senha é conferida mesmo quando o e-mail não existe (contra um hash falso): o BCrypt é lento de
        // propósito, e pular essa etapa faria o "e-mail inexistente" responder bem mais rápido, revelando
        // pelo tempo de resposta quais e-mails estão cadastrados
        String hash = user.map(User::getPasswordHash).orElseGet(dummyHash);
        boolean valid = passwordEncoder.matches(request.password(), hash) && user.isPresent();
        if (!valid) {
            rateLimiter.loginFailed(email);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }
        rateLimiter.loginSucceeded(email);
        return new AuthResponse(jwtService.generateToken(user.get()), BEARER, jwtService.expiresInSeconds());
    }

    // Minúsculas e sem espaços nas pontas, para o mesmo e-mail digitado de formas diferentes ser um só
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static BusinessException emailAlreadyRegistered() {
        return new BusinessException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
    }
}
