package com.myck.productivity.telegram.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Comando recebido pelo bot, como "/concluir 1 3".
 *
 * @param name nome em minúsculas, sem a barra e sem o "@nome_do_bot" que o Telegram acrescenta em grupos
 * @param args argumentos separados por espaço ou vírgula
 */
public record BotCommand(String name, List<String> args) {

    /** Lê uma mensagem que começa com "/". */
    public static BotCommand parse(String text) {
        String[] parts = text.strip().split("\\s+", 2);
        String name = parts[0].substring(1);
        int at = name.indexOf('@');
        if (at >= 0) {
            name = name.substring(0, at);
        }
        List<String> args = parts.length < 2
                ? List.of()
                : Arrays.stream(parts[1].split("[\\s,;]+")).filter(arg -> !arg.isBlank()).toList();
        return new BotCommand(name.toLowerCase(Locale.ROOT), args);
    }
}
