package com.myck.productivity.telegram.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera os códigos de vinculação. Usa SecureRandom (imprevisível, ao contrário de Random) e um alfabeto sem
 * caracteres que se confundem ao digitar no celular (0/O, 1/I/L).
 */
@Component
public class LinkCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    /** Código de 8 caracteres: 31^8 ≈ 850 bilhões de combinações, e ele só vale por 10 minutos. */
    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
