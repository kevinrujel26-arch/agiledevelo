-- =====================================================================
-- MaquiRenta: esquema inicial de la base de datos (PostgreSQL 14+)
--
-- Reúne en un solo archivo lo que antes eran 9 migraciones y contiene el
-- esquema completo y actual: usuarios, sesiones, categorías, máquinas,
-- fotos, bloqueos de disponibilidad, reservas por horas, pagos,
-- solicitudes de reembolso y auditoría.
--
-- Las bases que ya existen (producción) no lo vuelven a ejecutar: el
-- ejecutor de migraciones lo reconoce como aplicado por su nombre.
-- =====================================================================

-- Permite usar "=" sobre enteros dentro de un índice GiST (restricciones
-- de exclusión que impiden horarios superpuestos).
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
-- USUARIOS
-- Se puede entrar con correo y contraseña o con Google (google_id).
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre              VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  correo              VARCHAR(160) NOT NULL,
  contrasena_hash     VARCHAR(100),                       -- PBKDF2-SHA256 con sal; NULL si la cuenta solo usa Google
  rol                 VARCHAR(20)  NOT NULL DEFAULT 'CLIENTE'
                        CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR')),
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  intentos_fallidos   SMALLINT     NOT NULL DEFAULT 0,    -- bloqueo por intentos fallidos
  bloqueado_hasta     TIMESTAMPTZ,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  telefono            VARCHAR(9),                         -- celular peruano normalizado; NULL en cuentas antiguas
  google_id           VARCHAR(255),                       -- claim "sub" del ID token de Google
  CONSTRAINT ck_usuarios_correo_minusculas CHECK (correo = lower(correo)),
  CONSTRAINT ck_usuarios_telefono CHECK (telefono IS NULL OR telefono ~ '^9[0-9]{8}$'),
  CONSTRAINT ux_usuarios_google_id UNIQUE (google_id),
  -- Toda cuenta necesita al menos una forma de entrar
  CONSTRAINT ck_usuarios_forma_de_ingreso CHECK (contrasena_hash IS NOT NULL OR google_id IS NOT NULL)
);
-- El correo no puede repetirse
CREATE UNIQUE INDEX ux_usuarios_correo ON usuarios (correo);

CREATE TRIGGER tg_usuarios_actualizado
  BEFORE UPDATE ON usuarios
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- SESIONES (expiración por inactividad, cerrar sesión, recordarme)
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
-- CATEGORÍAS
-- ---------------------------------------------------------------------
CREATE TABLE categorias (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  nombre              VARCHAR(80)  NOT NULL CHECK (length(trim(nombre)) > 0),
  descripcion         VARCHAR(255),
  activa              BOOLEAN      NOT NULL DEFAULT TRUE,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- El nombre no se repite (sin distinguir mayúsculas)
CREATE UNIQUE INDEX ux_categorias_nombre ON categorias (lower(trim(nombre)));

CREATE TRIGGER tg_categorias_actualizado
  BEFORE UPDATE ON categorias
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- MÁQUINAS
-- El alquiler se cobra por hora: total = (tarifa + combustible) x horas.
-- El operador siempre va incluido; el combustible lo paga el cliente.
-- ---------------------------------------------------------------------
CREATE TABLE maquinas (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  -- Exactamente una categoría (NOT NULL + FK)
  categoria_id        INTEGER      NOT NULL REFERENCES categorias(id) ON DELETE RESTRICT,
  nombre              VARCHAR(120) NOT NULL CHECK (length(trim(nombre)) > 0),
  marca               VARCHAR(80)  NOT NULL,
  modelo              VARCHAR(80)  NOT NULL,
  descripcion         TEXT,
  especificaciones    JSONB        NOT NULL DEFAULT '{}'::jsonb, -- ej. {"Potencia":"120 HP"}
  tarifa_horaria      NUMERIC(10,2) NOT NULL,
  ubicacion           VARCHAR(160) NOT NULL,
  -- BORRADOR -> PUBLICADA -> RETIRADA (y RETIRADA -> PUBLICADA otra vez)
  estado              VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR'
                        CHECK (estado IN ('BORRADOR', 'PUBLICADA', 'RETIRADA')),
  en_mantenimiento    BOOLEAN      NOT NULL DEFAULT FALSE,
  publicada_en        TIMESTAMPTZ,
  creado_por          INTEGER      REFERENCES usuarios(id),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  -- Horas de uso de una máquina usada; horasUso se calcula = este valor + horas de reservas FINALIZADAS
  horometro_inicial   NUMERIC(10,1) NOT NULL DEFAULT 0,
  incluye_operador    BOOLEAN      NOT NULL DEFAULT TRUE,   -- sin uso: el operador siempre va incluido
  combustible_incluido BOOLEAN     NOT NULL DEFAULT FALSE,  -- sin uso: el combustible se cobra con combustible_por_hora
  combustible_por_hora NUMERIC(10,2) NOT NULL DEFAULT 0,    -- S/ por hora, lo paga el cliente
  -- nombre heredado de cuando la tarifa era diaria
  CONSTRAINT maquinas_tarifa_diaria_check CHECK (tarifa_horaria > 0),
  CONSTRAINT ck_maquinas_horometro_inicial CHECK (horometro_inicial >= 0),
  CONSTRAINT maquinas_combustible_por_hora_check CHECK (combustible_por_hora >= 0)
);
CREATE INDEX ix_maquinas_catalogo  ON maquinas (estado, categoria_id);
CREATE INDEX ix_maquinas_busqueda  ON maquinas (lower(nombre), lower(marca));

CREATE TRIGGER tg_maquinas_actualizado
  BEFORE UPDATE ON maquinas
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- FOTOS DE MÁQUINA (hasta 5, JPG/PNG, una principal)
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
-- BLOQUEOS DE DISPONIBILIDAD (mantenimiento u otros motivos)
-- Rango semiabierto [fecha_inicio, fecha_fin): fecha_fin es la medianoche
-- del día siguiente al último día bloqueado.
-- ---------------------------------------------------------------------
CREATE TABLE bloqueos_disponibilidad (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  maquina_id          INTEGER      NOT NULL REFERENCES maquinas(id) ON DELETE CASCADE,
  fecha_inicio        TIMESTAMPTZ  NOT NULL,
  fecha_fin           TIMESTAMPTZ  NOT NULL,
  motivo              VARCHAR(160),
  creado_por          INTEGER      REFERENCES usuarios(id),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT ck_bloqueo_rango CHECK (fecha_fin > fecha_inicio),
  -- Dos bloqueos de la misma máquina no pueden superponerse
  CONSTRAINT ex_bloqueos_sin_superposicion EXCLUDE USING gist (
    maquina_id WITH =,
    tstzrange(fecha_inicio, fecha_fin, '[)') WITH &&
  )
);

-- ---------------------------------------------------------------------
-- RESERVAS (por horas)
-- Horario 07:00 a 19:00, mínimo 1 hora entera, una reserva = un solo día.
-- fin_bloqueo = fin + 1 hora de preparación entre alquileres de la misma
-- máquina. Las reservas CANCELADAS liberan sus horas.
-- ---------------------------------------------------------------------
CREATE TABLE reservas (
  id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  maquina_id          INTEGER      NOT NULL REFERENCES maquinas(id) ON DELETE RESTRICT,
  cliente_id          INTEGER      NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
  fecha_inicio        TIMESTAMPTZ  NOT NULL,
  fecha_fin           TIMESTAMPTZ  NOT NULL,
  -- Se copia la tarifa al reservar para que un cambio de precio no altere reservas previas
  tarifa_horaria      NUMERIC(10,2) NOT NULL,
  monto_total         NUMERIC(12,2) NOT NULL CHECK (monto_total > 0),
  estado              VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE_PAGO',
  expira_pago_en      TIMESTAMPTZ,
  cancelada_en        TIMESTAMPTZ,
  motivo_cancelacion  VARCHAR(160),
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  -- Horas completas de la reserva (se redondea hacia arriba)
  horas               INTEGER GENERATED ALWAYS AS (
                        CEIL(EXTRACT(EPOCH FROM (fecha_fin - fecha_inicio)) / 3600.0)
                      ) STORED,
  codigo              VARCHAR(20) UNIQUE,            -- código de reserva que ve el cliente
  -- Datos del solicitante
  nombre_solicitante  VARCHAR(160),
  ruc                 VARCHAR(11),
  telefono_contacto   VARCHAR(20),
  email_contacto      VARCHAR(160),
  ubicacion_obra      VARCHAR(200),
  detalle             VARCHAR(300),
  acepta_politica     BOOLEAN,
  fin_bloqueo         TIMESTAMPTZ  NOT NULL,         -- fecha_fin + margen de 1 hora
  combustible_total   NUMERIC(10,2) NOT NULL DEFAULT 0,  -- parte del monto_total que corresponde a combustible
  -- nombre heredado de cuando la tarifa era diaria
  CONSTRAINT reservas_tarifa_diaria_check CHECK (tarifa_horaria > 0),
  CONSTRAINT reservas_estado_check
    CHECK (estado IN ('PENDIENTE_PAGO', 'PAGADA', 'FINALIZADA', 'CANCELADA', 'EN_CURSO')),
  CONSTRAINT ck_reserva_rango CHECK (fecha_fin > fecha_inicio),   -- mínimo 1 hora
  -- Dos reservas no canceladas de la misma máquina no pueden ocupar el mismo horario (margen incluido)
  CONSTRAINT ex_reservas_sin_solapamiento EXCLUDE USING gist (
    maquina_id WITH =,
    tstzrange(fecha_inicio, fin_bloqueo, '[)') WITH &&
  ) WHERE (estado <> 'CANCELADA')
);
CREATE INDEX ix_reservas_cliente ON reservas (cliente_id, creado_en DESC);
CREATE INDEX ix_reservas_maquina ON reservas (maquina_id, fecha_inicio);
CREATE INDEX ix_reservas_estado  ON reservas (estado);
CREATE INDEX ix_reservas_codigo  ON reservas (codigo);
CREATE INDEX ix_reservas_nombre_solicitante ON reservas (nombre_solicitante);

CREATE TRIGGER tg_reservas_actualizado
  BEFORE UPDATE ON reservas
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();

-- ---------------------------------------------------------------------
-- PAGOS (Mercado Pago)
-- La plataforma NO guarda datos de tarjeta.
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
  numero_comprobante  VARCHAR(30),              -- comprobante interno
  respuesta_proveedor JSONB,
  pagado_en           TIMESTAMPTZ,
  creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
  actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Un aviso (webhook) duplicado no crea ni confirma el pago dos veces
CREATE UNIQUE INDEX ux_pagos_proveedor_externo ON pagos (proveedor, id_externo)
  WHERE id_externo IS NOT NULL;
CREATE UNIQUE INDEX ux_pagos_comprobante ON pagos (numero_comprobante)
  WHERE numero_comprobante IS NOT NULL;
CREATE INDEX ix_pagos_reserva ON pagos (reserva_id);

CREATE TRIGGER tg_pagos_actualizado
  BEFORE UPDATE ON pagos
  FOR EACH ROW EXECUTE FUNCTION fn_set_actualizado_en();
-- ---------------------------------------------------------------------
-- SOLICITUDES DE REEMBOLSO
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
-- AUDITORÍA (qué administrador hizo el cambio y cuándo)
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