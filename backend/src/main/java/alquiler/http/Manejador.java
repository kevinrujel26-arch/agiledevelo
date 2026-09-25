package alquiler.http;

/** Atiende una petición y devuelve la respuesta (equivale a un método de un controlador). */
@FunctionalInterface
public interface Manejador {
    Respuesta manejar(Solicitud solicitud) throws Exception;
}
