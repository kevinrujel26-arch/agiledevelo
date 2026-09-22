package pe.upao.alquiler;

import pe.upao.alquiler.auth.AuthControlador;
import pe.upao.alquiler.auth.AuthServicio;
import pe.upao.alquiler.auth.SesionRepositorio;
import pe.upao.alquiler.auth.UsuarioRepositorio;
import pe.upao.alquiler.bd.Bd;
import pe.upao.alquiler.categorias.CategoriaControlador;
import pe.upao.alquiler.categorias.CategoriaRepositorio;
import pe.upao.alquiler.categorias.CategoriaServicio;
import pe.upao.alquiler.config.Config;
import pe.upao.alquiler.disponibilidad.BloqueoRepositorio;
import pe.upao.alquiler.disponibilidad.DisponibilidadControlador;
import pe.upao.alquiler.disponibilidad.DisponibilidadServicio;
import pe.upao.alquiler.http.Enrutador;
import pe.upao.alquiler.http.Respuesta;
import pe.upao.alquiler.http.Servidor;
import pe.upao.alquiler.json.Json;
import pe.upao.alquiler.maquinas.AlmacenFotos;
import pe.upao.alquiler.maquinas.FotoRepositorio;
import pe.upao.alquiler.maquinas.MaquinaControlador;
import pe.upao.alquiler.maquinas.MaquinaRepositorio;
import pe.upao.alquiler.maquinas.MaquinaServicio;
import pe.upao.alquiler.seguridad.Autenticador;
import pe.upao.alquiler.seguridad.Contrasenas;
import pe.upao.alquiler.seguridad.Jwt;

import java.time.Instant;

import static pe.upao.alquiler.http.Enrutador.Acceso.PUBLICO;

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
                new AlmacenFotos(config.dirSubidas), config);
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
