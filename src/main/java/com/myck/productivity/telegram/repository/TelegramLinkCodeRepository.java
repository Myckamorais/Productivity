package com.myck.productivity.telegram.repository;

import com.myck.productivity.telegram.entity.TelegramLinkCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Códigos de vinculação; a chave é o próprio código. */
public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, String> {

    // O código ativo do usuário (há no máximo um)
    Optional<TelegramLinkCode> findByUserId(Long userId);
}
