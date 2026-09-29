-- =====================================================================
-- Horas de uso de la máquina (horómetro).
--
-- horasUso NO se guarda: se calcula al consultar como
--   horometro_inicial + suma de reservas.horas con estado FINALIZADA
-- Es una APROXIMACIÓN (horas alquiladas a través del sistema), no una
-- lectura real del motor. Por eso el administrador puede fijar un
-- horómetro inicial, por ejemplo cuando registra una máquina usada.
--
-- Cuando el flujo de reservas (Sprint 3) pase una reserva a FINALIZADA,
-- la cifra sube sola, sin más cambios en la base de datos.
-- =====================================================================

ALTER TABLE maquinas
  ADD COLUMN horometro_inicial NUMERIC(10,1) NOT NULL DEFAULT 0
    CONSTRAINT ck_maquinas_horometro_inicial CHECK (horometro_inicial >= 0);
