package com.myck.productivity.telegram.repository;

import com.myck.productivity.telegram.entity.TelegramLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Vínculos usuário ↔ chat; a chave é o id do usuário. */
public interface TelegramLinkRepository extends JpaRepository<TelegramLink, Long> {

    // Quem é o dono do chat que mandou a mensagem
    Optional<TelegramLink> findByChatId(Long chatId);
}
