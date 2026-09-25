package alquiler.bd;

import alquiler.config.Config;
import alquiler.json.Json;
import alquiler.seguridad.Contrasenas;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga datos iniciales: el administrador, categorías y máquinas de ejemplo.
 * Se puede ejecutar varias veces sin duplicar datos.
 */
public final class Sembrador {

    private Sembrador() {
    }

    private static final List<String[]> CATEGORIAS = List.of(
            new String[]{"Excavadoras", "Excavadoras sobre orugas y sobre ruedas"},
            new String[]{"Retroexcavadoras", "Equipos de carga frontal y excavación trasera"},
            new String[]{"Cargadores frontales", "Cargadores de ruedas para movimiento de material"},
            new String[]{"Rodillos compactadores", "Compactación de suelos y asfalto"},
            new String[]{"Montacargas", "Elevación y traslado de carga en almacén u obra"},
            new String[]{"Minicargadores", "Equipos compactos para espacios reducidos"});

    private record MaquinaEjemplo(String categoria, String nombre, String marca, String modelo, int tarifa,
                                  String ubicacion, String estado, Map<String, Object> especificaciones) {
    }

    private static final List<MaquinaEjemplo> MAQUINAS = List.of(
            new MaquinaEjemplo("Excavadoras", "Excavadora hidráulica 320", "Caterpillar", "320 GC", 1450, "Trujillo", "PUBLICADA",
                    Json.obj("Peso operativo", "22 t", "Potencia", "146 HP", "Capacidad del cucharón", "1.2 m³")),
            new MaquinaEjemplo("Excavadoras", "Miniexcavadora 35G", "John Deere", "35G", 620, "Trujillo", "PUBLICADA",
                    Json.obj("Peso operativo", "3.6 t", "Potencia", "23 HP", "Profundidad de excavación", "3.4 m")),
            new MaquinaEjemplo("Retroexcavadoras", "Retroexcavadora 416F2", "Caterpillar", "416F2", 780, "Chiclayo", "PUBLICADA",
                    Json.obj("Potencia", "87 HP", "Tracción", "4x4", "Capacidad del cargador", "1 m³")),
            new MaquinaEjemplo("Cargadores frontales", "Cargador frontal 950GC", "Caterpillar", "950 GC", 1300, "Lima", "PUBLICADA",
                    Json.obj("Potencia", "225 HP", "Capacidad del cucharón", "3.1 m³")),
            new MaquinaEjemplo("Rodillos compactadores", "Rodillo vibratorio CA2500", "Dynapac", "CA2500D", 690, "Trujillo", "PUBLICADA",
                    Json.obj("Peso operativo", "10.5 t", "Ancho de tambor", "2.13 m")),
            new MaquinaEjemplo("Montacargas", "Montacargas diésel 3 t", "Toyota", "8FD30", 280, "Lima", "PUBLICADA",
                    Json.obj("Capacidad", "3000 kg", "Altura de elevación", "4.7 m", "Combustible", "Diésel")),
            new MaquinaEjemplo("Minicargadores", "Minicargador S650", "Bobcat", "S650", 450, "Piura", "BORRADOR",
                    Json.obj("Potencia", "74 HP", "Carga operativa", "1250 kg")));

    public static void sembrar(Bd bd, Config config) {
        Contrasenas contrasenas = new Contrasenas(config.iteracionesContrasena);
        String correoAdmin = config.valor("ADMIN_CORREO", "admin@alquiler.pe").toLowerCase();
        String claveAdmin = config.valor("ADMIN_CONTRASENA", "Admin12345");
        String nombreAdmin = config.valor("ADMIN_NOMBRE", "Administrador");

        bd.transaccion(tx -> {
            // Administrador (si ya existe, se le asegura el rol y la contraseña del .env)
            long adminId = tx.uno("""
                    INSERT INTO usuarios (nombre, correo, contrasena_hash, rol)
                    VALUES (?, ?, ?, 'ADMINISTRADOR')
                    ON CONFLICT (correo) DO UPDATE
                       SET rol = 'ADMINISTRADOR', contrasena_hash = EXCLUDED.contrasena_hash,
                           intentos_fallidos = 0, bloqueado_hasta = NULL
                    RETURNING id""", nombreAdmin, correoAdmin, contrasenas.cifrar(claveAdmin)).entero("id");
            System.out.println("[OK] Administrador: " + correoAdmin);

            Map<String, Long> idsCategoria = new HashMap<>();
            for (String[] c : CATEGORIAS) {
                Fila existente = tx.uno("SELECT id FROM categorias WHERE lower(nombre) = lower(?)", c[0]);
                long id = existente != null ? existente.entero("id")
                        : tx.uno("INSERT INTO categorias (nombre, descripcion) VALUES (?, ?) RETURNING id", c[0], c[1]).entero("id");
                idsCategoria.put(c[0], id);
            }
            System.out.println("[OK] " + CATEGORIAS.size() + " categorías");

            int creadas = 0;
            for (MaquinaEjemplo m : MAQUINAS) {
                if (tx.uno("SELECT 1 FROM maquinas WHERE nombre = ?", m.nombre()) != null) continue;
                tx.ejecutar("""
                        INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_diaria, ubicacion, estado,
                                              especificaciones, descripcion, publicada_en, creado_por)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, CASE WHEN ?::boolean THEN now() END, ?)""",
                        idsCategoria.get(m.categoria()), m.nombre(), m.marca(), m.modelo(), m.tarifa(), m.ubicacion(),
                        m.estado(), m.especificaciones(),
                        m.nombre() + " " + m.marca() + " en excelente estado, con mantenimiento al día. Incluye manual de operación.",
                        "PUBLICADA".equals(m.estado()), adminId);
                creadas++;
            }
            System.out.println("[OK] " + creadas + " máquina(s) de ejemplo nuevas");
            return null;
        });
    }
}
