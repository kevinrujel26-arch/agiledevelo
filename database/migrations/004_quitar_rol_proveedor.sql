-- =====================================================================
-- Se elimina el rol PROVEEDOR que había reservado la migración 002.
-- Las máquinas son del negocio: no existen proveedores externos, así
-- que los únicos roles son CLIENTE y ADMINISTRADOR.
--
-- (No confundir con pagos.proveedor, que es la pasarela de pago.)
-- =====================================================================

-- Por si algún usuario llegó a guardarse con ese rol, pasa a CLIENTE.
UPDATE usuarios SET rol = 'CLIENTE' WHERE rol = 'PROVEEDOR';

ALTER TABLE usuarios DROP CONSTRAINT usuarios_rol_check;

ALTER TABLE usuarios
  ADD CONSTRAINT usuarios_rol_check CHECK (rol IN ('CLIENTE', 'ADMINISTRADOR'));
