package alquiler.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabla de rutas: método + patrón ("/api/maquinas/{id}") -> manejador.
 * Cada ruta indica quién puede usarla (EN-05).
 */
public final class Enrutador {

    public enum Acceso {
        /** Cualquiera, sin iniciar sesión */
        PUBLICO,
        /** Cualquier usuario con sesión */
        AUTENTICADO,
        /** Solo rol ADMINISTRADOR */
        ADMINISTRADOR
    }

    public record Ruta(String metodo, String patron, String[] segmentos, Acceso acceso, Manejador manejador) {
    }

    public record Coincidencia(Ruta ruta, Map<String, String> params) {
    }

    private final List<Ruta> rutas = new ArrayList<>();

    public Enrutador get(String patron, Acceso acceso, Manejador m) {
        return agregar("GET", patron, acceso, m);
    }

    public Enrutador post(String patron, Acceso acceso, Manejador m) {
        return agregar("POST", patron, acceso, m);
    }

    public Enrutador put(String patron, Acceso acceso, Manejador m) {
        return agregar("PUT", patron, acceso, m);
    }

    public Enrutador patch(String patron, Acceso acceso, Manejador m) {
        return agregar("PATCH", patron, acceso, m);
    }

    public Enrutador delete(String patron, Acceso acceso, Manejador m) {
        return agregar("DELETE", patron, acceso, m);
    }

    private Enrutador agregar(String metodo, String patron, Acceso acceso, Manejador m) {
        rutas.add(new Ruta(metodo, patron, dividir(patron), acceso, m));
        return this;
    }

    /** Busca la ruta que coincide; devuelve null si no existe. */
    public Coincidencia buscar(String metodo, String ruta) {
        String[] partes = dividir(ruta);
        for (Ruta r : rutas) {
            if (!r.metodo().equals(metodo) || r.segmentos().length != partes.length) continue;
            Map<String, String> params = new HashMap<>();
            boolean coincide = true;
            for (int i = 0; i < partes.length; i++) {
                String seg = r.segmentos()[i];
                if (seg.startsWith("{") && seg.endsWith("}")) {
                    params.put(seg.substring(1, seg.length() - 1), partes[i]);
                } else if (!seg.equals(partes[i])) {
                    coincide = false;
                    break;
                }
            }
            if (coincide) return new Coincidencia(r, params);
        }
        return null;
    }

    public List<Ruta> rutas() {
        return List.copyOf(rutas);
    }

    private static String[] dividir(String ruta) {
        String limpia = ruta;
        while (limpia.length() > 1 && limpia.endsWith("/")) limpia = limpia.substring(0, limpia.length() - 1);
        if (limpia.startsWith("/")) limpia = limpia.substring(1);
        return limpia.isEmpty() ? new String[0] : limpia.split("/");
    }
}
