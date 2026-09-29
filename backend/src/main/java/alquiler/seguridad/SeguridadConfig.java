package alquiler.seguridad;

import alquiler.config.Config;
import alquiler.http.EscritorJson;
import alquiler.http.ManejadorErrores;
import alquiler.json.Json;
import alquiler.util.ErrorApp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * EN-03 / EN-05: Spring Security.
 *
 *  - Sin sesiones de servidor (STATELESS): cada petición trae su JWT.
 *  - /api/admin/**  → solo ADMINISTRADOR.
 *  - /api/auth/yo y /api/auth/logout → cualquier usuario con sesión.
 *  - Todo lo demás es público (catálogo, categorías, login, registro, fotos).
 *  - CORS: solo los orígenes de CORS_ORIGIN.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, Autenticador autenticador, Config config) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // no hay cookies: el token va en la cabecera Authorization
                .cors(c -> c.configurationSource(origenesPermitidos(config)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole(Rol.ADMINISTRADOR.name())
                        .requestMatchers("/api/auth/yo", "/api/auth/logout").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        // 401: sin sesión o sesión inválida (con el motivo que dejó el filtro)
                        .authenticationEntryPoint((peticion, respuesta, ex) -> {
                            ErrorApp motivo = peticion.getAttribute(FiltroJwt.ATRIBUTO_ERROR) instanceof ErrorApp m
                                    ? m : ErrorApp.noAutenticado();
                            EscritorJson.escribir(respuesta, 401, Json.obj("error", motivo.getMessage()));
                        })
                        // 403: hay sesión pero el rol no alcanza
                        .accessDeniedHandler((peticion, respuesta, ex) ->
                                EscritorJson.escribir(respuesta, 403, Json.obj("error", ErrorApp.prohibido().getMessage()))))
                .headers(h -> h
                        .cacheControl(c -> c.disable())
                        .frameOptions(f -> f.sameOrigin())
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .crossOriginResourcePolicy(c -> c.policy(CrossOriginResourcePolicy.CROSS_ORIGIN))) // deja mostrar las fotos desde el frontend
                .addFilterBefore(new FiltroJwt(autenticador, config), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource origenesPermitidos(Config config) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(config.corsOrigenes);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", cors);
        return fuente;
    }

    /**
     * El firewall de Spring Security rechaza rutas sospechosas ("../", "//", ";"...)
     * con un 400 sin cuerpo. Se responde como antes: 404 con el JSON de siempre.
     */
    @Bean
    public RequestRejectedHandler rutaRechazada() {
        return (peticion, respuesta, ex) ->
                EscritorJson.escribir(respuesta, 404, Json.obj("error", ManejadorErrores.mensajeRutaInexistente(peticion)));
    }

    /** Las contraseñas se siguen cifrando con nuestro PBKDF2 (mismo formato que ya está en la BD). */
    @Bean
    public PasswordEncoder codificadorDeContrasenas(Contrasenas contrasenas) {
        return new CodificadorContrasenas(contrasenas);
    }
}
