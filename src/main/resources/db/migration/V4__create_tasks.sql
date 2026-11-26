-- Fase 3: cria a tabela de tarefas.
-- category_id é obrigatório e SEM "ON DELETE CASCADE": apagar uma categoria que tem tarefas é recusado
-- (a aplicação responde 409). Também não usa RESTRICT, que é checado na hora e atrapalharia o
-- cascade de quando o usuário inteiro é apagado; o padrão (NO ACTION) confere só no fim do comando.
-- goal_id é opcional e usa ON DELETE SET NULL: apagar a meta só desvincula as tarefas, nada se perde.
-- user_id usa CASCADE: apagar o usuário apaga as tarefas dele.
CREATE TABLE tasks (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT                   NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id  BIGINT                   NOT NULL REFERENCES categories (id),
    goal_id      BIGINT                   REFERENCES goals (id) ON DELETE SET NULL,
    title        VARCHAR(150)             NOT NULL,
    description  VARCHAR(1000),
    priority     VARCHAR(10)              NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    planned_date DATE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED'))
);

-- Índices dos filtros da listagem (sempre por usuário) e das checagens por categoria e por meta
CREATE INDEX idx_tasks_user_date ON tasks (user_id, planned_date);
CREATE INDEX idx_tasks_user_status ON tasks (user_id, status);
CREATE INDEX idx_tasks_category ON tasks (category_id);
CREATE INDEX idx_tasks_goal ON tasks (goal_id);
