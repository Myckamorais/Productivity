-- Fase 1: cria a tabela de usuários.
-- O nome é "users" porque "user" é palavra reservada no PostgreSQL.
-- O e-mail é guardado em minúsculas e tem índice único (a regra de duplicidade vale também no banco).
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100)             NOT NULL,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(100)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);
