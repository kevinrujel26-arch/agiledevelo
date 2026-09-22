# Trazabilidad: criterios de aceptación → implementación → prueba

Sirve para la exposición del Sprint y para revisar la Definition of Done.

- El código Java está en `backend/src/main/java/pe/upao/alquiler/`.
- Las pruebas JUnit están en `backend/src/test/java/pe/upao/alquiler/`.
- Arquitectura por capas, sin frameworks: **Controlador** (HTTP) → **Servicio** (reglas de negocio) → **Repositorio** (SQL con JDBC).

## Sprint 1: Habilitadores

| Ítem | Implementación |
|---|---|
| EN-01 Repositorio, entorno y CI | Monorepo, `docker-compose.yml`, `.github/workflows/ci.yml` (JUnit con Maven y build del frontend en cada push) |
| EN-02 Base de datos | `database/migrations/001_esquema_inicial.sql`, `bd/Migraciones.java`, `bd/Bd.java` (JDBC + pool), `docs/base-de-datos.md` |
| EN-03 Autenticación base | `seguridad/Jwt.java` (JWT HS256), `seguridad/Contrasenas.java` (PBKDF2), `seguridad/Autenticador.java`, tabla `sesiones` |
| EN-04 Despliegue | `Dockerfile`, endpoint `GET /api/salud` en `Aplicacion.java` |
| EN-05 Control de acceso por rol | `Enrutador.Acceso` (PUBLICO / AUTENTICADO / ADMINISTRADOR), verificado en `http/Servidor.java`. En el frontend: `RutaProtegida` · Prueba: `AuthTest` |

## Sprint 2

### HU-01 Registrar cliente
| # | Criterio | Dónde | Prueba (`AuthTest`) |
|---|---|---|---|
| 1 | Correo no repetido | `AuthServicio.registrarCliente` + índice único | `correoRepetido` |
| 2 | Contraseña ≥ 8 y cifrada | `AuthControlador.registro` + `Contrasenas` (PBKDF2) | `contrasenaCorta`, `registraCliente` |
| 3 | Campos obligatorios | `Validador` + `Registro.jsx` | `camposObligatorios` |
| 4 | Correo con formato válido | `Validador.correo` + regex en el frontend | `correoInvalido` |
| 5 | Rol Cliente automático | El servicio fuerza `Rol.CLIENTE` | `registraCliente` |
| 6 | Mensaje de confirmación | Respuesta `mensaje` + `Registro.jsx` | `registraCliente` |

### HU-02 Iniciar y cerrar sesión
| # | Criterio | Dónde | Prueba (`AuthTest`) |
|---|---|---|---|
| 1 | Redirige según el rol | `panelSegunRol()` en `AuthContext.jsx` | Manual |
| 2 | Error genérico | `AuthServicio.MENSAJE_CREDENCIALES` | `errorGenerico` |
| 3 | Expira por inactividad | `Autenticador.autenticar` compara `ultima_actividad` | `expiraPorInactividad` |
| 4 | Cerrar sesión invalida el token | `SesionRepositorio.revocar` | `cerrarSesion` |
| 5 | Bloqueo tras 5 intentos | `UsuarioRepositorio.registrarIntentoFallido` → 423 durante 15 min | `bloqueoPorIntentos`, `reiniciaIntentos` |
| 6 | "Recordarme" | Sesión de 30 días; token en `localStorage` | `recordarme` |

### HU-14 Gestionar categorías
| # | Criterio | Dónde | Prueba (`CategoriaTest`) |
|---|---|---|---|
| 1 | Crear, renombrar, desactivar | `CategoriaControlador`, `AdminCategorias.jsx` | `crearRenombrarDesactivar` |
| 2 | Con máquinas no se elimina | `CategoriaServicio.eliminar` → 409 | `conMaquinasNoSeElimina` |
| 3 | Desactivada no aparece en el catálogo | `GET /api/categorias` solo devuelve activas | `desactivadaNoEsFiltro` |
| 4 | Nombre único | `nombreEnUso` + índice `lower(trim(nombre))` | `nombreUnico` |
| 5 | Máquina con exactamente una categoría | `categoria_id NOT NULL` + FK | Esquema de la BD |

### HU-08 Registrar y publicar máquina
| # | Criterio | Dónde | Prueba (`MaquinaTest`) |
|---|---|---|---|
| 1 | Datos obligatorios | `MaquinaControlador.leerDatos` + `AdminMaquinaForm.jsx` | `camposObligatorios` |
| 2 | Hasta 5 fotos JPG/PNG | `http/Multipart` + `AlmacenFotos.esImagenReal` + trigger en BD | `subeFotos`, `rechazaFormatos`, `maximoCincoFotos` |
| 3 | Foto principal | `FotoRepositorio.marcarPrincipal` + índice único parcial | `cambiaPrincipal`, `noPublicaSinFoto` |
| 4 | Guardar como borrador | Estado inicial `BORRADOR` | `borrador` |
| 5 | Al publicar aparece de inmediato | `MaquinaServicio.publicar` | `publicaEnCatalogo` |
| 6 | Editar después de publicada | `PUT /api/admin/maquinas/{id}` | `editaPublicada` |
| 7 | Retirar sin borrar el historial | Estado `RETIRADA`; reservas con `ON DELETE RESTRICT` | `retiraSinBorrarHistorial` |

### HU-09 Gestionar disponibilidad
| # | Criterio | Dónde | Prueba (`DisponibilidadTest`) |
|---|---|---|---|
| 1 | Uno o varios rangos | `DisponibilidadServicio.bloquear` en una transacción | `variosRangos` |
| 2 | Fechas bloqueadas no disponibles | `GET /api/maquinas/{id}/disponibilidad` | `apareceEnCalendario` |
| 3 | No bloquear sobre una reserva pagada | `BloqueoRepositorio.reservaPagadaQueSeCruza` | `noSobreReservaPagada` |
| 4 | Desbloquear | `DELETE .../bloqueos/{bloqueoId}` | `desbloquear` |
| 5 | Se refleja de inmediato | Consulta en vivo, sin caché | `apareceEnCalendario` |

### HU-03 Ver catálogo de maquinaria
| # | Criterio | Dónde | Prueba (`MaquinaTest`) |
|---|---|---|---|
| 1 | Solo publicadas | `MaquinaRepositorio.listarPublicadas` | `borrador`, `retiraSinBorrarHistorial` |
| 2 | Foto, nombre, categoría, tarifa | `Maquina.tarjetaJson` + `TarjetaMaquina` | `catalogoPublico` |
| 3 | Paginado | `Paginacion` + componente `Paginacion` | `paginado` |
| 4 | Visible sin sesión | Ruta `PUBLICO` | `catalogoPublico` |
| 5 | Enlaza al detalle | `/maquinas/:id` | `detallePorUrl` |

Además, `UtilidadesTest` contiene pruebas unitarias del lector JSON, JWT, contraseñas, fechas y multipart.

## Decisiones tomadas (para revisar con el Product Owner)
- **"Publicada y Activa" (HU-03):** una máquina es visible si su estado es `PUBLICADA`. Una máquina "en mantenimiento" sigue en el catálogo con una insignia, y en su detalle se muestra un aviso (HU-04 criterio 5).
- **Categoría desactivada:** deja de aparecer como filtro, pero sus máquinas publicadas siguen visibles. No se pueden asignar máquinas nuevas a una categoría inactiva, ni publicar una máquina cuya categoría esté inactiva.
- **Bloqueos:** no se pueden bloquear fechas pasadas ni superponer dos bloqueos. Se permite bloquear sobre una reserva *pendiente de pago*, porque el criterio solo menciona las pagadas; conviene confirmarlo antes del Sprint 3.
- **Sesión:** 30 min de inactividad y 8 h como máximo; con "Recordarme", 7 días de inactividad y 30 días como máximo. El bloqueo por intentos fallidos dura 15 min.
- **Sin frameworks (indicación del docente):** el backend usa solo el JDK. Las únicas dependencias son el driver JDBC de PostgreSQL y JUnit para las pruebas.
