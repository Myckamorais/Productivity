-- Fase 2: cria as tabelas de categorias e metas, ambas pertencentes a um usuário.
-- ON DELETE CASCADE: apagar o usuário apaga também as categorias e metas dele.
-- O nome da categoria é único por usuário sem diferenciar maiúsculas ("Estudos" e "estudos" colidem);
-- por isso o índice usa lower(name).
CREATE TABLE categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(60)              NOT NULL,
    color      VARCHAR(7)               NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_categories_user_name ON categories (user_id, lower(name));

CREATE TABLE goals (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(150)             NOT NULL,
    description VARCHAR(1000),
    deadline    DATE,
    status      VARCHAR(20)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_goals_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

-- Toda listagem filtra por usuário
CREATE INDEX idx_goals_user ON goals (user_id);
