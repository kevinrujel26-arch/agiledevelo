package pe.upao.alquiler.http;

/** Respuesta HTTP: código de estado + cuerpo que se convertirá a JSON. */
public record Respuesta(int estado, Object cuerpo) {

    public static Respuesta ok(Object cuerpo) {
        return new Respuesta(200, cuerpo);
    }

    public static Respuesta creado(Object cuerpo) {
        return new Respuesta(201, cuerpo);
    }

    public static Respuesta sinContenido() {
        return new Respuesta(204, null);
    }
}
