-- Migration base (Fase 0): não cria tabelas.
-- Serve para validar a conexão e inicializar o histórico do Flyway (flyway_schema_history).
-- As tabelas entram nas fases seguintes: V2 users (Fase 1), categorias/metas (Fase 2) e assim por diante.
SELECT 1;
