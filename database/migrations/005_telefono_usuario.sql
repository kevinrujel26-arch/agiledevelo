-- =====================================================================
-- HU-02 (backlog v7): celular del usuario.
--
-- Se guarda normalizado: solo los 9 dígitos de un celular peruano, que
-- siempre empieza con 9 (ej. 987654321). Es obligatorio al registrarse,
-- pero la columna admite NULL para los usuarios que ya existían antes
-- de este cambio (todavía no se les exige).
-- =====================================================================

ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(9);

ALTER TABLE usuarios
  ADD CONSTRAINT ck_usuarios_telefono CHECK (telefono IS NULL OR telefono ~ '^9[0-9]{8}$');
