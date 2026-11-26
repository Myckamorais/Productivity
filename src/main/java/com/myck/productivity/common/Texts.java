package com.myck.productivity.common;

/** Utilitários de texto compartilhados pelos services. */
public final class Texts {

    private Texts() {
    }

    /** Texto nulo ou em branco vira nulo, para o banco não guardar string vazia; senão devolve sem espaços nas pontas. */
    public static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
