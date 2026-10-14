-- =====================================================================
-- HU-06: Reservar máquina por horas
--
-- Extiende la tabla reservas existente (que ya es por horas desde la
-- migración 003) con campos de datos del solicitante, código único,
-- fin_bloqueo (para margen de 1 hora), y estados adicionales.
--
-- Horario de operación: 07:00 a 19:00 (configurables en backend).
-- Mínimo 1 hora por reserva, en horas enteras.
-- Una reserva ocupa un solo día.
-- Rango semiabierto [inicio, fin).
-- Margen entre alquileres: 1 hora (configurable).
--
-- Un cliente puede tener varias reservas PENDIENTE_PAGO a la vez,
-- pero máximo 2 (configurable).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ===== MAQUINAS: agregar condiciones del servicio =====
ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS incluye_operador BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE maquinas ADD COLUMN IF NOT EXISTS combustible_incluido BOOLEAN NOT NULL DEFAULT FALSE;

-- ===== RESERVAS: agregar campos para HU-06 =====
-- Código único para cada reserva
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS codigo VARCHAR(20) UNIQUE;

-- Datos del solicitante (obligatorios al crear reserva)
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS nombre_solicitante VARCHAR(160);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS ruc VARCHAR(11);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS telefono_contacto VARCHAR(20);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS email_contacto VARCHAR(160);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS ubicacion_obra VARCHAR(200);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS detalle VARCHAR(300);
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS acepta_politica BOOLEAN;

-- fin_bloqueo = fin + margen (1 hora), para el constraint de exclusión
-- Se calcula automáticamente como fin + 1 hour
ALTER TABLE reservas ADD COLUMN IF NOT EXISTS fin_bloqueo TIMESTAMPTZ;

-- Llenar fin_bloqueo para filas existentes (si las hay)
UPDATE reservas SET fin_bloqueo = fecha_fin + INTERVAL '1 hour' WHERE fin_bloqueo IS NULL;

-- Ahora hacerla NOT NULL
ALTER TABLE reservas ALTER COLUMN fin_bloqueo SET NOT NULL;

-- ===== ACTUALIZAR CONSTRAINT DE ESTADO =====
-- El constraint CHECK anterior (001) solo permite: PENDIENTE_PAGO, PAGADA, FINALIZADA, CANCELADA.
-- HU-06 agrega EN_CURSO (máquina entregada, en uso). Remover el CHECK anterior y recrearlo.
ALTER TABLE reservas DROP CONSTRAINT "reservas_estado_check";
ALTER TABLE reservas ADD CONSTRAINT reservas_estado_check
  CHECK (estado IN ('PENDIENTE_PAGO', 'PAGADA', 'FINALIZADA', 'CANCELADA', 'EN_CURSO'));

-- ===== REEMPLAZAR CONSTRAINT DE EXCLUSIÓN =====
-- El constraint anterior usaba daterange (para días).
-- Ahora usamos tstzrange con fin_bloqueo (para horas con margen).
-- Solo excluye reservas no CANCELADA.
ALTER TABLE reservas DROP CONSTRAINT ex_reservas_sin_superposicion;
ALTER TABLE reservas
  ADD CONSTRAINT ex_reservas_sin_solapamiento EXCLUDE USING gist (
    maquina_id WITH =,
    tstzrange(fecha_inicio, fin_bloqueo, '[)') WITH &&
  ) WHERE (estado <> 'CANCELADA');

-- ===== ÍNDICES ADICIONALES =====
CREATE INDEX IF NOT EXISTS ix_reservas_codigo ON reservas (codigo);
CREATE INDEX IF NOT EXISTS ix_reservas_nombre_solicitante ON reservas (nombre_solicitante);
