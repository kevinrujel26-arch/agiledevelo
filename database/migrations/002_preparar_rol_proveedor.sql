-- =====================================================================
-- Sprint 1 (v4): prepara la columna usuarios.rol para admitir PROVEEDOR.
-- No se activa ninguna ruta ni funcionalidad todavía: el registro sigue
-- creando solo CLIENTE, y solo CLIENTE/ADMINISTRADOR pueden iniciar
-- sesión con acceso a algo. Esto evita tener que migrar el esquema a
-- mitad del Sprint 2 cuando entre HU-09 (registro de proveedor).
-- =====================================================================

ALTER TABLE usuarios DROP CONSTRAINT usuarios_rol_check;

ALTER TABLE usuarios
  ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR', 'PROVEEDOR'));
