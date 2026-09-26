package alquiler;

import alquiler.auth.AuthControlador;
import alquiler.auth.AuthServicio;
import alquiler.auth.SesionRepositorio;
import alquiler.auth.UsuarioRepositorio;
import alquiler.bd.Bd;
import alquiler.categorias.CategoriaControlador;
import alquiler.categorias.CategoriaRepositorio;
import alquiler.categorias.CategoriaServicio;
import alquiler.config.Config;
import alquiler.disponibilidad.BloqueoRepositorio;
import alquiler.disponibilidad.DisponibilidadControlador;
import alquiler.disponibilidad.DisponibilidadServicio;
import alquiler.http.Enrutador;
import alquiler.http.Respuesta;
import alquiler.http.Servidor;
import alquiler.json.Json;
import alquiler.maquinas.AlmacenFotos;
import alquiler.maquinas.FotoRepositorio;
import alquiler.maquinas.MaquinaControlador;
import alquiler.maquinas.MaquinaRepositorio;
import alquiler.maquinas.MaquinaServicio;
import alquiler.seguridad.Autenticador;
import alquiler.seguridad.Contrasenas;
import alquiler.seguridad.Jwt;

import java.time.Instant;

import static alquiler.http.Enrutador.Acceso.PUBLICO;

/**
 * Arma la aplicación: crea cada objeto y le pasa lo que necesita
 * (inyección de dependencias "a mano", sin frameworks).
 *
 *   Controlador (HTTP) -> Servicio (reglas de negocio) -> Repositorio (SQL) -> Bd
 */
public final class Aplicacion {

    private final Config config;
    private final Bd bd;
    private final Servidor servidor;

    public Aplicacion(Config config) {
        this.config = config;
        this.bd = new Bd(config);

        Jwt jwt = new Jwt(config.jwtSecreto);
        Contrasenas contrasenas = new Contrasenas(config.iteracionesContrasena);

        // Repositorios
        UsuarioRepositorio usuarios = new UsuarioRepositorio(bd);
        SesionRepositorio sesiones = new SesionRepositorio(bd);
        CategoriaRepositorio categorias = new CategoriaRepositorio(bd);
        MaquinaRepositorio maquinas = new MaquinaRepositorio(bd);
        FotoRepositorio fotos = new FotoRepositorio();
        BloqueoRepositorio bloqueos = new BloqueoRepositorio();

        // Servicios
        AuthServicio authServicio = new AuthServicio(usuarios, sesiones, contrasenas, jwt, config);
        CategoriaServicio categoriaServicio = new CategoriaServicio(categorias);
        MaquinaServicio maquinaServicio = new MaquinaServicio(bd, maquinas, fotos, categorias,
                new AlmacenFotos(config), config);
        DisponibilidadServicio disponibilidadServicio = new DisponibilidadServicio(bd, bloqueos, maquinas, config);

        // Rutas
        Enrutador rutas = new Enrutador();
        rutas.get("/api/salud", PUBLICO, s -> {           // EN-04: verifica que la API y la BD respondan
            bd.uno("SELECT 1");
            return Respuesta.ok(Json.obj("estado", "ok", "fecha", Instant.now()));
        });
        new AuthControlador(authServicio).registrar(rutas);
        new CategoriaControlador(categoriaServicio).registrar(rutas);
        new MaquinaControlador(maquinaServicio, config).registrar(rutas);
        new DisponibilidadControlador(disponibilidadServicio, config).registrar(rutas);

        this.servidor = new Servidor(config, rutas, new Autenticador(bd, jwt, config));
    }

    public void iniciar(int puerto) throws Exception {
        servidor.iniciar(puerto);
    }

    public int puerto() {
        return servidor.puerto();
    }

    public void detener() {
        servidor.detener();
        bd.close();
    }

    public Bd bd() {
        return bd;
    }

    public Config config() {
        return config;
    }
}
