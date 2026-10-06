-- =====================================================================
-- Iniciar sesión / registrarse con Google.
--
-- google_id guarda el identificador de la cuenta de Google (claim "sub"
-- del ID token). Es único y NULL para las cuentas que solo usan correo
-- y contraseña.
--
-- Una cuenta creada con Google no tiene contraseña: contrasena_hash pasa
-- a admitir NULL, pero toda cuenta debe tener al menos una forma de entrar
-- (contraseña o Google). Con contrasena_hash NULL el login normal nunca
-- acepta ninguna contraseña.
--
-- usuarios.telefono ya admite NULL (005): a quien entra con Google se le
-- pide el celular después, desde "Mi cuenta".
-- =====================================================================

ALTER TABLE usuarios ADD COLUMN google_id VARCHAR(255);

ALTER TABLE usuarios ADD CONSTRAINT ux_usuarios_google_id UNIQUE (google_id);

ALTER TABLE usuarios ALTER COLUMN contrasena_hash DROP NOT NULL;

ALTER TABLE usuarios
  ADD CONSTRAINT ck_usuarios_forma_de_ingreso CHECK (contrasena_hash IS NOT NULL OR google_id IS NOT NULL);
