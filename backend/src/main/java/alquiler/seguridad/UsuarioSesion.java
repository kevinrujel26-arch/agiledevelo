package alquiler.seguridad;

import alquiler.json.Json;

import java.util.Map;

/** Usuario autenticado en la petición actual. */
public record UsuarioSesion(long id, String nombre, String correo, String telefono, Rol rol, String sesionId) implements Json.Convertible {

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    @Override
    public Map<String, Object> aJson() {
        return Json.obj("id", id, "nombre", nombre, "correo", correo, "telefono", telefono, "rol", rol.name());
    }
}
