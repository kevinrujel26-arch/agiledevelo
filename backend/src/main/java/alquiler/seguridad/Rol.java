package alquiler.seguridad;

/** Roles del sistema (EN-05). Coinciden con la columna usuarios.rol. */
public enum Rol {
    CLIENTE,
    ADMINISTRADOR,
    // Preparado desde el Sprint 1 (v4): el registro/login y la BD ya lo admiten,
    // pero ninguna ruta lo usa todavía. Se activa recién en el Sprint 2 (HU-09).
    PROVEEDOR
}
