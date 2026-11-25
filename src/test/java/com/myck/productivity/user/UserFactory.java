package com.myck.productivity.user;

import com.myck.productivity.user.entity.User;

/** Fábrica de usuários para os testes unitários, para não repetir a montagem em cada classe. */
public final class UserFactory {

    private UserFactory() {
    }

    /** Usuário padrão com o id informado. */
    public static User comId(Long id) {
        User user = new User("Ana Souza", "ana@exemplo.com", "$2a$10$hashDeTeste");
        user.setId(id);
        return user;
    }
}
