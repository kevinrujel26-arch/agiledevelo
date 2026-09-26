-- =====================================================================
-- El alquiler pasa de cobrarse "por día" a cobrarse "por hora".
--
-- 1) maquinas.tarifa_diaria y reservas.tarifa_diaria se renombran a
--    tarifa_horaria (el negocio real cobra por hora de uso).
-- 2) Las fechas de disponibilidad y reservas dejan de ser solo "día"
--    (DATE) y pasan a guardar fecha + hora (TIMESTAMPTZ), para que el
--    Sprint 3 (Reservar) pueda ofrecer horarios exactos, no solo días.
--
-- Nota para hoy: la pantalla de "Disponibilidad" del administrador
-- (HU-09) sigue bloqueando por días completos (por ejemplo, "la máquina
-- está en mantenimiento del 5 al 7") -- eso no cambia, porque sigue
-- teniendo sentido bloquear mantenimiento por días enteros aunque el
-- alquiler en sí se cobre por hora. Lo que cambia es que, por debajo,
-- la base de datos ya queda lista para guardar horas exactas cuando se
-- construya la reserva de clientes en el Sprint 3.
-- =====================================================================

-- ===================== MAQUINAS =====================
ALTER TABLE maquinas RENAME COLUMN tarifa_diaria TO tarifa_horaria;

-- ===================== BLOQUEOS_DISPONIBILIDAD =====================
ALTER TABLE bloqueos_disponibilidad DROP CONSTRAINT ex_bloqueos_sin_superposicion;
ALTER TABLE bloqueos_disponibilidad DROP CONSTRAINT ck_bloqueo_rango;

-- fecha_inicio se queda como medianoche de ese día; fecha_fin pasa a ser
-- la medianoche del día SIGUIENTE, para poder usar un rango "[inicio, fin)"
-- que cubre el día completo, igual que antes.
ALTER TABLE bloqueos_disponibilidad
  ALTER COLUMN fecha_inicio TYPE TIMESTAMPTZ USING fecha_inicio::timestamptz,
  ALTER COLUMN fecha_fin    TYPE TIMESTAMPTZ USING (fecha_fin + INTERVAL '1 day')::timestamptz;

ALTER TABLE bloqueos_disponibilidad
  ADD CONSTRAINT ck_bloqueo_rango CHECK (fecha_fin > fecha_inicio);

ALTER TABLE bloqueos_disponibilidad
  ADD CONSTRAINT ex_bloqueos_sin_superposicion EXCLUDE USING gist (
    maquina_id WITH =,
    tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&
  );

-- ===================== RESERVAS =====================
ALTER TABLE reservas DROP CONSTRAINT ex_reservas_sin_superposicion;
ALTER TABLE reservas DROP CONSTRAINT ck_reserva_rango;
ALTER TABLE reservas DROP COLUMN dias;

ALTER TABLE reservas
  ALTER COLUMN fecha_inicio TYPE TIMESTAMPTZ USING fecha_inicio::timestamptz,
  ALTER COLUMN fecha_fin    TYPE TIMESTAMPTZ USING (fecha_fin + INTERVAL '1 day')::timestamptz;

ALTER TABLE reservas RENAME COLUMN tarifa_diaria TO tarifa_horaria;

-- Reemplaza a la antigua columna "dias": horas completas de la reserva
-- (se redondea hacia arriba, igual que cualquier alquiler por hora real).
ALTER TABLE reservas
  ADD COLUMN horas INTEGER GENERATED ALWAYS AS (
    CEIL(EXTRACT(EPOCH FROM (fecha_fin - fecha_inicio)) / 3600.0)
  ) STORED;

ALTER TABLE reservas
  ADD CONSTRAINT ck_reserva_rango CHECK (fecha_fin > fecha_inicio);  -- mínimo 1 hora

ALTER TABLE reservas
  ADD CONSTRAINT ex_reservas_sin_superposicion EXCLUDE USING gist (
    maquina_id WITH =,
    tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&
  ) WHERE (estado IN ('PENDIENTE_PAGO', 'PAGADA'));
