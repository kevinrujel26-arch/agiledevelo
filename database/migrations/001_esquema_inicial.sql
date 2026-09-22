-- =====================================================================
-- EN-02: Diseño y creación de la base de datos inicial
-- Sistema de Alquiler de Maquinaria — PostgreSQL 14+
--
-- Incluye las tablas de TODOS los sprints para que el modelo de datos
-- quede completo desde el inicio:
--   Sprint 1-2: usuarios, sesiones, categorias, maquinas, fotos, bloqueos
--   Sprint 3-4: reservas, pagos, solicitudes de reembolso, auditoría
-- =====================================================================

-- Permite usar "=" sobre enteros dentro de un índice GiST (restricción
-- de exclusión que impide reservas con fechas superpuestas).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Función reutilizable para mantener actualizado_en
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_set_actualizado_en() RETURNS trigger AS $$
BEGIN
  NEW.actualizado_en := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
-- USUARIOS  (HU-01, HU-02, HU-15, EN-03, EN-05)
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre              VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  correo              VARCHAR(160) NOT NULL,
  contrasena_hash     VARCHAR(100) NOT NULL,          -- bcrypt, nunca texto plano
  rol                 VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE'
                        CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR')),
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,  -- HU-15
  intentos_fallidos   SMALLINT     NOT NULL DEFAULT 0,     -- HU-02 (bloqueo)
  bloqueado_hasta     TIMESTAMPTZ,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_usuarios_correo_minusculas CHECK (correo = lower(correo))
);
-- HU-01 criterio 1: el correo no puede repetirse
CREATE UNIQUE INDEX ux_usuarios_correo ON usuarios (correo);

CREATE TRIGGER tg_usuarios_actualizado
  BEFORE UPDATE ON usuarios
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- SESIONES  (HU-02: expiración por inactividad, cerrar sesión, recordarme)
-- Cada token JWT lleva el id de su sesión; al cerrar sesión se revoca aquí.
-- ---------------------------------------------------------------------
CREATE TABLE sesiones (
  id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id          INTEGER      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
  recordarme          BOOLEAN      NOT NULL DEFAULT FALSE,
  creada_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  ultima_actividad    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  expira_en           TIMESTAMPTZ  NOT NULL,             -- vencimiento absoluto
  revocada_en         TIMESTAMPTZ,
  ip                  VARCHAR(64),
  agente_usuario      VARCHAR(255)
);
CREATE INDEX ix_sesiones_usuario ON sesiones (usuario_id);

-- ---------------------------------------------------------------------
-- CATEGORÍAS  (HU-14)
-- ---------------------------------------------------------------------
CREATE TABLE categorias (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre              VARCHAR(80)  NOT NULL CHECK (length(trim(nombre)) > 0),
  descripcion         VARCHAR(255),
  activa              BOOLEAN      NOT NULL DEFAULT TRUE,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- HU-14 criterio 4: nombre no se repite (sin distinguir mayúsculas)
CREATE UNIQUE INDEX ux_categorias_nombre ON categorias (lower(trim(nombre)));

CREATE TRIGGER tg_categorias_actualizado
  BEFORE UPDATE ON categorias
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- MÁQUINAS  (HU-03, HU-04, HU-08)
-- ---------------------------------------------------------------------
CREATE TABLE maquinas (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- HU-14 criterio 5: exactamente una categoría (NOT NULL + FK)
  categoria_id        INTEGER      NOT NULL REFERENCES categorias(id) ON DELETE RESTRICT,
  nombre              VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  marca               VARCHAR(80)  NOT NULL,
  modelo              VARCHAR(80)  NOT NULL,
  descripcion         TEXT,
  especificaciones    JSONB        NOT NULL DEFAULT '{}'::jsonb, -- ej. {"Potencia":"120 HP"}
  tarifa_diaria       NUMERIC(10,2) NOT NULL CHECK (tarifa_diaria > 0),
  ubicacion           VARCHAR(160) NOT NULL,
  -- BORRADOR -> PUBLICADA -> RETIRADA (y RETIRADA -> PUBLICADA otra vez)
  estado              VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR'
                        CHECK (estado IN ('BORRADOR', 'PUBLICADA', 'RETIRADA')),
  en_mantenimiento    BOOLEAN      NOT NULL DEFAULT FALSE,   -- HU-04 criterio 5
  publicada_en        TIMESTAMPTZ,
  creado_por          INTEGER      REFERENCES usuarios(id),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_maquinas_catalogo  ON maquinas (estado, categoria_id);
CREATE INDEX ix_maquinas_busqueda  ON maquinas (lower(nombre), lower(marca));

CREATE TRIGGER tg_maquinas_actualizado
  BEFORE UPDATE ON maquinas
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- FOTOS DE MÁQUINA  (HU-08: hasta 5, JPG/PNG, una principal)
-- ---------------------------------------------------------------------
CREATE TABLE fotos_maquina (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  maquina_id          INTEGER      NOT NULL REFERENCES maquinas(id) ON DELETE CASCADE,
  ruta                VARCHAR(255) NOT NULL,   -- ruta pública, ej. /uploads/maquinas/abc.jpg
  tipo_mime           VARCHAR(20)  NOT NULL CHECK (tipo_mime IN ('image/jpeg', 'image/png')),
  es_principal        BOOLEAN      NOT NULL DEFAULT FALSE,
  orden               SMALLINT     NOT NULL DEFAULT 0,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_fotos_maquina ON fotos_maquina (maquina_id);
-- Solo una foto principal por máquina
CREATE UNIQUE INDEX ux_fotos_una_principal ON fotos_maquina (maquina_id) WHERE es_principal;

-- Máximo 5 fotos por máquina (se valida también en el backend)
CREATE OR REPLACE FUNCTION fn_max_fotos_maquina() RETURNS trigger AS $$
BEGIN
  -- Serializa inserciones concurrentes sobre la misma máquina
  PERFORM 1 FROM maquinas WHERE id = NEW.maquina_id FOR UPDATE;
  IF (SELECT count(*) FROM fotos_maquina WHERE maquina_id = NEW.maquina_id) >= 5 THEN
    RAISE EXCEPTION 'Una máquina puede tener como máximo 5 fotos'
      USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_max_5_fotos';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_fotos_max
  BEFORE INSERT ON fotos_maquina
  FOR EACH ROW EXECUTE FUNCTION fn_max_fotos_maquina();

-- ---------------------------------------------------------------------
-- BLOQUEOS DE DISPONIBILIDAD  (HU-09)
-- Rango de fechas inclusivo [fecha_inicio, fecha_fin]
-- ---------------------------------------------------------------------
CREATE TABLE bloqueos_disponibilidad (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  maquina_id          INTEGER      NOT NULL REFERENCES maquinas(id) ON DELETE CASCADE,
  fecha_inicio        DATE         NOT NULL,
  fecha_fin           DATE         NOT NULL,
  motivo              VARCHAR(160),
  creado_por          INTEGER      REFERENCES usuarios(id),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_bloqueo_rango CHECK (fecha_fin >= fecha_inicio),
  -- Dos bloqueos de la misma máquina no pueden superponerse
  CONSTRAINT ex_bloqueos_sin_superposicion EXCLUDE USING gist (
    maquina_id WITH =,
    daterange(fecha_inicio, fecha_fin, '[]') WITH &&
  )
);

-- ---------------------------------------------------------------------
-- RESERVAS  (HU-05, HU-07, HU-10, HU-12)  — se usa desde el Sprint 3
-- ---------------------------------------------------------------------
CREATE TABLE reservas (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  maquina_id          INTEGER      NOT NULL REFERENCES maquinas(id) ON DELETE RESTRICT,
  cliente_id          INTEGER      NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
  fecha_inicio        DATE         NOT NULL,
  fecha_fin           DATE         NOT NULL,
  -- Se copia la tarifa al reservar para que un cambio de precio no altere reservas previas
  tarifa_diaria       NUMERIC(10,2) NOT NULL CHECK (tarifa_diaria > 0),
  dias                INTEGER      GENERATED ALWAYS AS (fecha_fin - fecha_inicio + 1) STORED,
  monto_total         NUMERIC(12,2) NOT NULL CHECK (monto_total > 0),
  estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE_PAGO'
                        CHECK (estado IN ('PENDIENTE_PAGO', 'PAGADA', 'FINALIZADA', 'CANCELADA')),
  expira_pago_en      TIMESTAMPTZ,              -- HU-05 criterio 5
  cancelada_en        TIMESTAMPTZ,
  motivo_cancelacion  VARCHAR(160),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_reserva_rango CHECK (fecha_fin >= fecha_inicio),   -- mínimo 1 día
  -- HU-05 criterio 3: dos reservas vigentes no pueden ocupar las mismas fechas
  CONSTRAINT ex_reservas_sin_superposicion EXCLUDE USING gist (
    maquina_id WITH =,
    daterange(fecha_inicio, fecha_fin, '[]') WITH &&
  ) WHERE (estado IN ('PENDIENTE_PAGO', 'PAGADA'))
);
CREATE INDEX ix_reservas_cliente ON reservas (cliente_id, creado_en DESC);
CREATE INDEX ix_reservas_maquina ON reservas (maquina_id, fecha_inicio);
CREATE INDEX ix_reservas_estado  ON reservas (estado);

CREATE TRIGGER tg_reservas_actualizado
  BEFORE UPDATE ON reservas
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- PAGOS  (HU-06, HU-13) — Mercado Pago, se usa desde el Sprint 4
-- La plataforma NO guarda datos de tarjeta (HU-06 criterio 1).
-- ---------------------------------------------------------------------
CREATE TABLE pagos (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reserva_id          INTEGER      NOT NULL REFERENCES reservas(id) ON DELETE RESTRICT,
  proveedor           VARCHAR(30)  NOT NULL DEFAULT 'MERCADO_PAGO',
  preferencia_id      VARCHAR(100),             -- id de la preferencia de Checkout Pro
  id_externo          VARCHAR(100),             -- payment_id que devuelve Mercado Pago
  estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'REEMBOLSADO')),
  monto               NUMERIC(12,2) NOT NULL CHECK (monto > 0),
  moneda              CHAR(3)      NOT NULL DEFAULT 'PEN',
  numero_comprobante  VARCHAR(30),              -- comprobante interno (HU-13)
  respuesta_proveedor JSONB,
  pagado_en           TIMESTAMPTZ,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- HU-06 criterio 4: un aviso (webhook) duplicado no crea/confirma dos veces
CREATE UNIQUE INDEX ux_pagos_proveedor_externo ON pagos (proveedor, id_externo)
  WHERE id_externo IS NOT NULL;
CREATE UNIQUE INDEX ux_pagos_comprobante ON pagos (numero_comprobante)
  WHERE numero_comprobante IS NOT NULL;
CREATE INDEX ix_pagos_reserva ON pagos (reserva_id);

CREATE TRIGGER tg_pagos_actualizado
  BEFORE UPDATE ON pagos
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- SOLICITUDES DE REEMBOLSO  (HU-12 criterio 2)
-- ---------------------------------------------------------------------
CREATE TABLE solicitudes_reembolso (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  reserva_id          INTEGER      NOT NULL UNIQUE REFERENCES reservas(id) ON DELETE RESTRICT,
  pago_id             INTEGER      NOT NULL REFERENCES pagos(id) ON DELETE RESTRICT,
  monto               NUMERIC(12,2) NOT NULL CHECK (monto > 0),
  estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                        CHECK (estado IN ('PENDIENTE', 'PROCESADO', 'RECHAZADO')),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  procesado_en        TIMESTAMPTZ
);

-- ---------------------------------------------------------------------
-- AUDITORÍA  (HU-15 criterio 5: qué administrador hizo el cambio y cuándo)
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
  id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  usuario_id          INTEGER      REFERENCES usuarios(id),   -- quién lo hizo
  accion              VARCHAR(60)  NOT NULL,                  -- ej. CLIENTE_DESACTIVADO
  entidad             VARCHAR(40)  NOT NULL,                  -- ej. usuarios
  entidad_id          INTEGER,
  detalle             JSONB,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_id);
