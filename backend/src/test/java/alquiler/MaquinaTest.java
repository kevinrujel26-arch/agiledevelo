package alquiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import alquiler.json.Json;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas de aceptación: HU-08 Registrar y publicar máquina · HU-03 Ver catálogo. */
class MaquinaTest extends PruebaBase {

    private String token;
    private long categoriaId;

    @BeforeEach
    void preparar() {
        token = tokenAdmin();
        categoriaId = crearCategoria("Excavadoras", true);
    }

    private long crearMaquina(Map<String, Object> datos) {
        Resp r = post("/api/admin/maquinas", datos, token);
        assertEquals(201, r.estado(), "crear máquina: " + r.cuerpo());
        return numero(r.json().get("id"));
    }

    private long crearPublicada(String nombre) {
        Map<String, Object> datos = datosMaquina(categoriaId);
        datos.put("nombre", nombre);
        long id = crearMaquina(datos);
        assertEquals(201, subir("/api/admin/maquinas/" + id + "/fotos", token, new Archivo("foto.png", "image/png", PNG)).estado());
        assertEquals(200, post("/api/admin/maquinas/" + id + "/publicar", null, token).estado());
        return id;
    }

    private static List<Long> ids(Resp catalogo) {
        return catalogo.lista("datos").stream().map(m -> numero(m.get("id"))).toList();
    }

    // ------------------------------------------------------------ HU-08
    @Test
    @DisplayName("HU-08: pide categoría, marca, modelo, tarifa y ubicación")
    void camposObligatorios() {
        Resp r = post("/api/admin/maquinas", Json.obj("nombre", "Sin datos"), token);
        assertEquals(400, r.estado());
        List<Object> campos = r.lista("detalles").stream().map(d -> d.get("campo")).map(Object.class::cast).toList();
        for (String campo : new String[]{"categoriaId", "marca", "modelo", "tarifaHoraria", "ubicacion"}) {
            assertTrue(campos.contains(campo), "falta el error de " + campo);
        }
    }

    @Test
    @DisplayName("HU-08: se guarda como borrador y no aparece en el catálogo")
    void borrador() {
        long id = crearMaquina(datosMaquina(categoriaId));
        assertEquals("BORRADOR", get("/api/admin/maquinas/" + id, token).json().get("estado"));
        assertEquals(0, get("/api/maquinas", null).lista("datos").size());
        assertEquals(404, get("/api/maquinas/" + id, null).estado());
    }

    @Test
    @DisplayName("HU-08: sube fotos JPG/PNG y la primera queda como principal")
    void subeFotos() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Resp r = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("a.jpg", "image/jpeg", JPG), new Archivo("b.png", "image/png", PNG));
        assertEquals(201, r.estado());
        List<Map<String, Object>> fotos = r.lista("fotos");
        assertEquals(2, fotos.size());
        assertEquals(1, fotos.stream().filter(f -> Boolean.TRUE.equals(f.get("esPrincipal"))).count());
    }

    @Test
    @DisplayName("HU-08: rechaza formatos que no son JPG o PNG")
    void rechazaFormatos() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Resp gif = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("a.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(400, gif.estado());
        // Archivo que dice ser PNG pero no lo es
        Resp falso = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("falso.png", "image/png", "no soy imagen".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(400, falso.estado());
    }

    @Test
    @DisplayName("HU-08: permite como máximo 5 fotos")
    void maximoCincoFotos() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Archivo[] cinco = new Archivo[5];
        for (int i = 0; i < 5; i++) cinco[i] = new Archivo("f" + i + ".png", "image/png", PNG);
        assertEquals(201, subir("/api/admin/maquinas/" + id + "/fotos", token, cinco).estado());
        Resp sexta = subir("/api/admin/maquinas/" + id + "/fotos", token, new Archivo("f6.png", "image/png", PNG));
        assertEquals(400, sexta.estado());
        assertTrue(sexta.error().contains("máximo de 5"));
    }

    @Test
    @DisplayName("HU-08: se puede cambiar la foto principal")
    void cambiaPrincipal() {
        long id = crearMaquina(datosMaquina(categoriaId));
        Resp r = subir("/api/admin/maquinas/" + id + "/fotos", token,
                new Archivo("a.png", "image/png", PNG), new Archivo("b.png", "image/png", PNG));
        long otra = r.lista("fotos").stream().filter(f -> !Boolean.TRUE.equals(f.get("esPrincipal")))
                .map(f -> numero(f.get("id"))).findFirst().orElseThrow();
        Resp cambio = patch("/api/admin/maquinas/" + id + "/fotos/" + otra + "/principal", null, token);
        long principal = cambio.lista("fotos").stream().filter(f -> Boolean.TRUE.equals(f.get("esPrincipal")))
                .map(f -> numero(f.get("id"))).findFirst().orElseThrow();
        assertEquals(otra, principal);
    }

    @Test
    @DisplayName("HU-08: no se publica sin foto principal")
    void noPublicaSinFoto() {
        long id = crearMaquina(datosMaquina(categoriaId));
        assertEquals(409, post("/api/admin/maquinas/" + id + "/publicar", null, token).estado());
    }

    @Test
    @DisplayName("HU-08: al publicarla aparece de inmediato en el catálogo")
    void publicaEnCatalogo() {
        long id = crearPublicada("Excavadora 320");
        assertTrue(ids(get("/api/maquinas", null)).contains(id));
    }

    @Test
    @DisplayName("HU-08: se puede editar después de publicada")
    void editaPublicada() {
        long id = crearPublicada("Excavadora 320");
        Resp r = put("/api/admin/maquinas/" + id, Json.obj("tarifaHoraria", 1600.5), token);
        assertEquals(200, r.estado());
        assertEquals(1600.5, ((Number) r.json().get("tarifaHoraria")).doubleValue(), 0.001);
        assertEquals("PUBLICADA", r.json().get("estado"));
    }

    @Test
    @DisplayName("HU-08: se retira del catálogo sin borrar su historial de reservas")
    void retiraSinBorrarHistorial() {
        long id = crearPublicada("Excavadora 320");
        long adminId = bd().uno("SELECT id FROM usuarios WHERE rol = 'ADMINISTRADOR'").entero("id");
        bd().ejecutar("""
                INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, tarifa_horaria, monto_total, estado)
                VALUES (?, ?, '2025-01-10', '2025-01-12', 1450, 4350, 'FINALIZADA')""", id, adminId);

        assertEquals(200, post("/api/admin/maquinas/" + id + "/retirar", null, token).estado());
        assertEquals(0, get("/api/maquinas", null).lista("datos").size());
        assertEquals(1L, bd().uno("SELECT count(*) AS total FROM reservas WHERE maquina_id = ?", id).enteroOCero("total"));
    }

    // ------------------------------------------------------------ HU-03
    @Test
    @DisplayName("HU-03: visible sin sesión, con foto, nombre, categoría y tarifa")
    void catalogoPublico() {
        crearPublicada("Excavadora 320");
        Resp r = get("/api/maquinas", null);
        assertEquals(200, r.estado());
        Map<String, Object> tarjeta = r.lista("datos").get(0);
        assertEquals("Excavadora 320", tarjeta.get("nombre"));
        assertEquals("Excavadoras", ((Map<?, ?>) tarjeta.get("categoria")).get("nombre"));
        assertEquals(1450L, numero(tarjeta.get("tarifaHoraria")));
        assertTrue(((String) tarjeta.get("fotoPrincipal")).matches("^/uploads/maquinas/.+\\.png$"));
    }

    @Test
    @DisplayName("HU-03: el catálogo está paginado")
    void paginado() {
        for (int i = 0; i < 5; i++) crearPublicada("Máquina " + i);
        Resp r = get("/api/maquinas?pagina=2&tamanio=2", null);
        assertEquals(2, r.lista("datos").size());
        Map<String, Object> p = r.objeto("paginacion");
        assertEquals(2L, numero(p.get("pagina")));
        assertEquals(5L, numero(p.get("total")));
        assertEquals(3L, numero(p.get("totalPaginas")));
    }

    // ------------------------------------------------------------ HU-06: rango de precio
    private long crearPublicada(String nombre, long categoria, Object tarifaHoraria) {
        Map<String, Object> datos = datosMaquina(categoria);
        datos.put("nombre", nombre);
        datos.put("tarifaHoraria", tarifaHoraria);
        long id = crearMaquina(datos);
        assertEquals(201, subir("/api/admin/maquinas/" + id + "/fotos", token, new Archivo("foto.png", "image/png", PNG)).estado());
        assertEquals(200, post("/api/admin/maquinas/" + id + "/publicar", null, token).estado());
        return id;
    }

    /** Cuatro máquinas publicadas de S/ 50, 100, 150 y 200 por hora. Devuelve sus ids en ese orden. */
    private long[] crearRangoDePrecios() {
        return new long[]{
                crearPublicada("Minicargador", categoriaId, 50),
                crearPublicada("Retroexcavadora", categoriaId, 100),
                crearPublicada("Excavadora", categoriaId, "150.00"),
                crearPublicada("Cargador frontal", categoriaId, 200)};
    }

    private static long total(Resp r) {
        return numero(r.objeto("paginacion").get("total"));
    }

    @Test
    @DisplayName("HU-06: filtra solo con precio mínimo (inclusivo)")
    void soloPrecioMinimo() {
        long[] m = crearRangoDePrecios();
        Resp r = get("/api/maquinas?precioMin=150", null);
        assertEquals(200, r.estado());
        assertEquals(Set.of(m[2], m[3]), Set.copyOf(ids(r)));
        assertEquals(2L, total(r));
    }

    @Test
    @DisplayName("HU-06: filtra solo con precio máximo (inclusivo)")
    void soloPrecioMaximo() {
        long[] m = crearRangoDePrecios();
        Resp r = get("/api/maquinas?precioMax=100", null);
        assertEquals(Set.of(m[0], m[1]), Set.copyOf(ids(r)));
        assertEquals(2L, total(r));
    }

    @Test
    @DisplayName("HU-06: filtra con mínimo y máximo, incluyendo los bordes")
    void rangoInclusivo() {
        long[] m = crearRangoDePrecios();
        assertEquals(Set.of(m[1], m[2]), Set.copyOf(ids(get("/api/maquinas?precioMin=100&precioMax=150", null))));
        assertEquals(List.of(m[1]), ids(get("/api/maquinas?precioMin=100&precioMax=100", null)));
        assertEquals(Set.of(m[1], m[2]), Set.copyOf(ids(get("/api/maquinas?precioMin=99.99&precioMax=150.01", null))));
        Resp vacio = get("/api/maquinas?precioMin=100.01&precioMax=149.99", null);
        assertEquals(200, vacio.estado());
        assertEquals(0, vacio.lista("datos").size());
        assertEquals(0L, total(vacio));
    }

    @Test
    @DisplayName("HU-06: si el mínimo es mayor que el máximo responde 400 con un mensaje claro")
    void minimoMayorQueMaximo() {
        Resp r = get("/api/maquinas?precioMin=200&precioMax=100", null);
        assertEquals(400, r.estado());
        assertEquals("precioMin", r.lista("detalles").get(0).get("campo"));
        assertTrue(r.error().contains("no puede ser mayor que el precio máximo"), r.error());
    }

    @Test
    @DisplayName("HU-06: rechaza precios negativos o que no son números")
    void precioInvalido() {
        assertEquals(400, get("/api/maquinas?precioMin=-1", null).estado());
        assertEquals(400, get("/api/maquinas?precioMax=abc", null).estado());
        assertEquals(200, get("/api/maquinas?precioMin=0", null).estado());
    }

    @Test
    @DisplayName("HU-06: el rango de precio se combina con la categoría y la búsqueda")
    void combinaConCategoriaYBusqueda() {
        long[] m = crearRangoDePrecios();
        long otraCategoria = crearCategoria("Montacargas", true);
        long montacargas = crearPublicada("Montacargas diésel", otraCategoria, 120);

        assertEquals(List.of(montacargas),
                ids(get("/api/maquinas?categoriaId=" + otraCategoria + "&precioMin=100&precioMax=150", null)));
        assertEquals(Set.of(m[1], m[2]),
                Set.copyOf(ids(get("/api/maquinas?categoriaId=" + categoriaId + "&precioMin=100&precioMax=150", null))));
        // "cargador" encuentra Minicargador (50) y Cargador frontal (200); el rango deja solo el segundo
        Resp r = get("/api/maquinas?q=cargador&precioMin=100", null);
        assertEquals(List.of(m[3]), ids(r));
        assertEquals(1L, total(r));
    }

    @Test
    @DisplayName("HU-06: la paginación y el total respetan el rango de precio")
    void paginacionConRango() {
        for (int i = 0; i < 5; i++) crearPublicada("Barata " + i, categoriaId, 40 + i);
        for (int i = 0; i < 3; i++) crearPublicada("Cara " + i, categoriaId, 500 + i);

        Resp p1 = get("/api/maquinas?precioMax=100&pagina=1&tamanio=2", null);
        Resp p3 = get("/api/maquinas?precioMax=100&pagina=3&tamanio=2", null);
        assertEquals(5L, total(p1));
        assertEquals(3L, numero(p1.objeto("paginacion").get("totalPaginas")));
        assertEquals(2, p1.lista("datos").size());
        assertEquals(1, p3.lista("datos").size());
        p1.lista("datos").forEach(t -> assertTrue(((Number) t.get("tarifaHoraria")).doubleValue() <= 100));
    }

    @Test
    @DisplayName("HU-06: el listado del administrador también acepta el rango de precio")
    void rangoEnAdmin() {
        long[] m = crearRangoDePrecios();
        Resp r = get("/api/admin/maquinas?precioMin=150&precioMax=150", token);
        assertEquals(200, r.estado());
        assertEquals(List.of(m[2]), ids(r));
        assertEquals(400, get("/api/admin/maquinas?precioMin=10&precioMax=5", token).estado());
    }

    // ------------------------------------------------------------ Horas de uso (horómetro)
    private static double horas(Object valor) {
        return ((Number) valor).doubleValue();
    }

    /** Inserta por SQL una reserva de la máquina con la duración y el estado dados. */
    private static void insertarReserva(long maquinaId, String inicio, String fin, String estado) {
        long clienteId = crearCliente().id();
        bd().ejecutar("""
                INSERT INTO reservas (maquina_id, cliente_id, fecha_inicio, fecha_fin, tarifa_horaria, monto_total, estado)
                VALUES (?, ?, ?::timestamptz, ?::timestamptz, 100, 1000, ?)""", maquinaId, clienteId, inicio, fin, estado);
    }

    @Test
    @DisplayName("Horas de uso: una máquina nueva tiene 0 horas")
    void horasUsoMaquinaNueva() {
        long id = crearPublicada("Excavadora 320");
        Resp admin = get("/api/admin/maquinas/" + id, token);
        assertEquals(0.0, horas(admin.json().get("horasUso")));
        assertEquals(0.0, horas(admin.json().get("horometroInicial")));
        assertEquals(0.0, horas(get("/api/maquinas/" + id, null).json().get("horasUso")));
    }

    @Test
    @DisplayName("Horas de uso: horómetro inicial + reservas FINALIZADAS (no cuentan las PAGADAS ni CANCELADAS)")
    void horasUsoConReservas() {
        Map<String, Object> datos = datosMaquina(categoriaId);
        datos.put("horometroInicial", 500);
        long id = crearMaquina(datos);
        assertEquals(500.0, horas(get("/api/admin/maquinas/" + id, token).json().get("horasUso")));

        insertarReserva(id, "2025-01-10 08:00-05", "2025-01-10 18:00-05", "FINALIZADA"); // 10 h
        insertarReserva(id, "2025-02-01 08:00-05", "2025-02-01 12:00-05", "PAGADA");     // no cuenta
        insertarReserva(id, "2025-03-01 08:00-05", "2025-03-01 20:00-05", "CANCELADA");  // no cuenta

        Resp admin = get("/api/admin/maquinas/" + id, token);
        assertEquals(510.0, horas(admin.json().get("horasUso")));
        assertEquals(500.0, horas(admin.json().get("horometroInicial")));

        // El listado del administrador no duplica la máquina por tener varias reservas
        Resp lista = get("/api/admin/maquinas", token);
        assertEquals(1L, numero(lista.objeto("paginacion").get("total")));
        assertEquals(1, lista.lista("datos").size());
        assertEquals(510.0, horas(lista.lista("datos").get(0).get("horasUso")));
    }

    @Test
    @DisplayName("Horas de uso: al editar el horómetro inicial se actualiza el resultado")
    void editarHorometro() {
        long id = crearPublicada("Excavadora 320");
        insertarReserva(id, "2025-01-10 08:00-05", "2025-01-10 18:00-05", "FINALIZADA");
        Resp r = put("/api/admin/maquinas/" + id, Json.obj("horometroInicial", "1250.5"), token);
        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals(1250.5, horas(r.json().get("horometroInicial")));
        assertEquals(1260.5, horas(r.json().get("horasUso")));

        // El público ve las horas de uso (en el detalle y en el catálogo) pero no el horómetro inicial
        Resp publico = get("/api/maquinas/" + id, null);
        assertEquals(1260.5, horas(publico.json().get("horasUso")));
        assertTrue(!publico.json().containsKey("horometroInicial"));
        assertEquals(1260.5, horas(get("/api/maquinas", null).lista("datos").get(0).get("horasUso")));
    }

    @Test
    @DisplayName("Horas de uso: el horómetro inicial debe estar entre 0 y 999999.9")
    void horometroInvalido() {
        for (Object malo : new Object[]{-1, "abc", 1000000}) {
            Map<String, Object> datos = datosMaquina(categoriaId);
            datos.put("horometroInicial", malo);
            Resp r = post("/api/admin/maquinas", datos, token);
            assertEquals(400, r.estado(), String.valueOf(malo));
            assertEquals("horometroInicial", r.lista("detalles").get(0).get("campo"));
        }
        Map<String, Object> datos = datosMaquina(categoriaId);
        datos.put("horometroInicial", "999999.9");
        assertEquals(201, post("/api/admin/maquinas", datos, token).estado());
    }

    // ------------------------------------------------------------ Validaciones de máquinas
    private Resp crearCon(String campo, Object valor) {
        Map<String, Object> datos = datosMaquina(categoriaId);
        datos.put(campo, valor);
        return post("/api/admin/maquinas", datos, token);
    }

    private void assertError(String campo, Object valor, String mensaje) {
        Resp r = crearCon(campo, valor);
        assertEquals(400, r.estado(), campo + "=" + valor);
        assertEquals(mensaje, r.errorDe(campo), campo + "=" + valor);
    }

    @Test
    @DisplayName("Validación: nombre, marca y modelo admiten letras, números, espacios y - . / + ( )")
    void textosMaquinaValidos() {
        Map<String, Object> datos = datosMaquina(categoriaId);
        datos.put("nombre", "  Excavadora   320-D (2020) 1.5/2+ ");
        datos.put("marca", "John Deere");
        datos.put("modelo", "8FD30");
        Resp r = post("/api/admin/maquinas", datos, token);
        assertEquals(201, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("Excavadora 320-D (2020) 1.5/2+", r.json().get("nombre"));
    }

    @Test
    @DisplayName("Validación: nombre 2–120 y marca/modelo 1–80, sin símbolos raros y con alguna letra o número")
    void textosMaquinaInvalidos() {
        assertError("nombre", "Excavadora #1", "El nombre solo puede contener letras, números, espacios y los signos - . / + ( )");
        assertError("nombre", "---", "El nombre debe contener al menos una letra o un número");
        assertError("nombre", "E", "El nombre debe tener al menos 2 caracteres");
        assertError("nombre", "x".repeat(121), "El nombre debe tener como máximo 120 caracteres");
        assertError("nombre", "<script>", "El nombre no puede contener los signos < ni >");
        assertError("marca", "   ", "La marca es obligatoria");
        assertError("marca", "m".repeat(81), "La marca debe tener como máximo 80 caracteres");
        assertError("modelo", "320 & GC", "El modelo solo puede contener letras, números, espacios y los signos - . / + ( )");
        assertError("modelo", "320\tGC", "El modelo contiene caracteres no permitidos");
    }

    @Test
    @DisplayName("Validación: la tarifa por hora es mayor que 0, con máximo 2 decimales y hasta 100000")
    void tarifaReglas() {
        assertError("tarifaHoraria", 0, "La tarifa por hora debe ser mayor a 0");
        assertError("tarifaHoraria", -5, "La tarifa por hora debe ser mayor a 0");
        assertError("tarifaHoraria", "12.345", "La tarifa por hora puede tener como máximo 2 decimales");
        assertError("tarifaHoraria", 100000.01, "La tarifa por hora no puede ser mayor a 100000");
        assertError("tarifaHoraria", "abc", "La tarifa por hora debe ser un número");
        assertError("tarifaHoraria", "", "La tarifa por hora es obligatoria");
        Resp ok = crearCon("tarifaHoraria", "99.90");
        assertEquals(201, ok.estado(), String.valueOf(ok.cuerpo()));
        assertEquals(99.9, ((Number) ok.json().get("tarifaHoraria")).doubleValue(), 0.001);
        assertEquals(201, crearCon("tarifaHoraria", 100000).estado());
    }

    @Test
    @DisplayName("Validación: ubicación 2–160 con al menos una letra; descripción hasta 2000 y sin HTML")
    void ubicacionYDescripcion() {
        assertError("ubicacion", "L", "La ubicación debe tener al menos 2 caracteres");
        assertError("ubicacion", "123", "La ubicación debe contener al menos una letra");
        assertError("ubicacion", "u".repeat(161), "La ubicación debe tener como máximo 160 caracteres");
        assertError("descripcion", "d".repeat(2001), "La descripción debe tener como máximo 2000 caracteres");
        assertError("descripcion", "Buena <script>alert(1)</script>", "La descripción no puede contener los signos < ni >");
        Resp ok = crearCon("descripcion", "  Línea 1\nLínea 2  ");
        assertEquals(201, ok.estado(), String.valueOf(ok.cuerpo()));
        long id = numero(ok.json().get("id"));
        assertEquals("Línea 1\nLínea 2", get("/api/admin/maquinas/" + id, token).json().get("descripcion"));
    }

    @Test
    @DisplayName("Validación: el horómetro inicial admite como máximo 1 decimal")
    void horometroDecimales() {
        assertError("horometroInicial", "12.35", "El horómetro inicial puede tener como máximo 1 decimal");
        assertError("horometroInicial", -1, "El horómetro inicial no puede ser negativo");
        assertError("horometroInicial", 1000000, "El horómetro inicial no puede ser mayor a 999999.9");
        assertEquals(201, crearCon("horometroInicial", 12.5).estado());
    }

    @Test
    @DisplayName("Validación: especificaciones con máximo 30 pares, nombre ≤ 60, valor ≤ 200, sin vacíos ni repetidos")
    void especificacionesReglas() {
        Map<String, Object> muchas = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 31; i++) muchas.put("Dato " + i, "x");
        assertError("especificaciones", muchas, "Puedes agregar como máximo 30 especificaciones");
        assertError("especificaciones", Json.obj(" ", "146 HP"), "Cada especificación necesita un nombre");
        assertError("especificaciones", Json.obj("Potencia", "146 HP", "potencia ", "150 HP"),
                "La especificación \"potencia\" está repetida");
        assertError("especificaciones", Json.obj("n".repeat(61), "1"),
                "El nombre de una especificación debe tener como máximo 60 caracteres");
        assertError("especificaciones", Json.obj("Potencia", "v".repeat(201)),
                "El valor de \"Potencia\" debe tener como máximo 200 caracteres");
        assertError("especificaciones", Json.obj("Potencia", "<b>146</b>"),
                "Las especificaciones no pueden contener los signos < ni >");
        Resp ok = crearCon("especificaciones", Json.obj("  Capacidad  del cucharón ", " 1.2 m³ "));
        assertEquals(201, ok.estado(), String.valueOf(ok.cuerpo()));
        long id = numero(ok.json().get("id"));
        assertEquals(Map.of("Capacidad del cucharón", "1.2 m³"), get("/api/admin/maquinas/" + id, token).json().get("especificaciones"));
    }

    @Test
    @DisplayName("Validación: una máquina antigua con datos fuera de las reglas se edita sin tocar esos campos")
    void maquinaAntiguaSeEdita() {
        long id = bd().uno("""
                INSERT INTO maquinas (categoria_id, nombre, marca, modelo, tarifa_horaria, ubicacion)
                VALUES (?, 'Máquina #1 & Co', 'Marca*', '320', 150, '1') RETURNING id""", categoriaId).entero("id");
        Resp r = put("/api/admin/maquinas/" + id, Json.obj("tarifaHoraria", 175), token);
        assertEquals(200, r.estado(), String.valueOf(r.cuerpo()));
        assertEquals("Máquina #1 & Co", r.json().get("nombre"));
        // Si se envía el campo, sí se aplica la regla nueva
        assertEquals(400, put("/api/admin/maquinas/" + id, Json.obj("nombre", "Máquina #1 & Co"), token).estado());
    }

    @Test
    @DisplayName("HU-03/HU-04: el detalle de una máquina publicada se abre por su URL")
    void detallePorUrl() {
        long id = crearPublicada("Excavadora 320");
        Resp r = get("/api/maquinas/" + id, null);
        assertEquals(200, r.estado());
        assertEquals(Map.of("Potencia", "146 HP"), r.json().get("especificaciones"));
        assertEquals(1, r.lista("fotos").size());
    }

    @Test
    @DisplayName("HU-08: las fotos subidas se pueden descargar desde /uploads")
    void sirveFotos() {
        long id = crearPublicada("Excavadora 320");
        String ruta = (String) get("/api/maquinas/" + id, null).lista("fotos").get(0).get("url");
        Descarga d = descargar(ruta);
        assertEquals(200, d.estado());
        assertEquals("image/png", d.tipo());
        assertEquals(PNG.length, d.datos().length);
        assertEquals(404, descargar("/uploads/../pom.xml").estado());
    }
}
