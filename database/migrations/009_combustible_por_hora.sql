-- Migración 009: Combustible por hora
-- Agrega columna combustible_por_hora a maquinas y combustible_total a reservas
-- Idempotente: puede ejecutarse múltiples veces sin error

ALTER TABLE maquinas
ADD COLUMN IF NOT EXISTS combustible_por_hora NUMERIC(10,2) NOT NULL DEFAULT 0
CHECK (combustible_por_hora >= 0);

ALTER TABLE reservas
ADD COLUMN IF NOT EXISTS combustible_total NUMERIC(10,2) NOT NULL DEFAULT 0;
