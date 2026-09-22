# Trazabilidad: criterios de aceptación → implementación → prueba

Sirve para la exposición del Sprint y para la revisión (Definition of Done). Las rutas de archivos son relativas al repositorio. "Prueba" indica el archivo de `backend/tests/` que verifica el criterio automáticamente.

## Sprint 1: Habilitadores

| Ítem | Implementación |
|---|---|
| EN-01 Repositorio, entorno y CI | Estructura monorepo, `docker-compose.yml`, `.github/workflows/ci.yml` (pruebas del backend y build del frontend en cada push) |
| EN-02 Base de datos | `database/migrations/001_esquema_inicial.sql`, `backend/scripts/migrar.js`, `docs/base-de-datos.md` |
| EN-03 Autenticación base | `backend/src/modulos/auth/`, `backend/src/middleware/autenticacion.js` (JWT + tabla `sesiones`) |
| EN-04 Despliegue | `render.yaml`, endpoint `GET /api/salud` |
| EN-05 Control de acceso por rol | `autorizar()` en `middleware/autenticacion.js`; todo `/api/admin/*` exige ADMINISTRADOR. En el frontend: `RutaProtegida` en `componentes/comunes.jsx` · Prueba: `auth.test.js` |

## Sprint 2

### HU-01 Registrar cliente
| # | Criterio | Dónde | Prueba |
|---|---|---|---|
| 1 | Correo no repetido | `auth.servicio.js` + índice único en BD | `auth.test.js` |
| 2 | Contraseña ≥ 8 y cifrada | Zod en `auth.rutas.js` + bcrypt | `auth.test.js` |
| 3 | Campos obligatorios | Zod + validación en `Registro.jsx` | `auth.test.js` |
| 4 | Correo con formato válido | Zod `.email()` + regex en el frontend | `auth.test.js` |
| 5 | Rol Cliente automático | El servicio fuerza `CLIENTE` e ignora `rol` enviado | `auth.test.js` |
| 6 | Mensaje de confirmación | `Registro.jsx` redirige al login con el mensaje | Manual |

### HU-02 Iniciar y cerrar sesión
| # | Criterio | Dónde | Prueba |
|---|---|---|---|
| 1 | Redirige según el rol | `panelSegunRol()` en `AuthContext.jsx` | Manual |
| 2 | Error genérico | `MENSAJE_CREDENCIALES` en `auth.servicio.js` | `auth.test.js` |
| 3 | Expira por inactividad | `autenticar()` compara `ultima_actividad` | `auth.test.js` |
| 4 | Cerrar sesión invalida el token | `sesiones.revocada_en` | `auth.test.js` |
| 5 | Bloqueo tras 5 intentos | `registrarIntentoFallido()` → 423 durante 15 min | `auth.test.js` |
| 6 | "Recordarme" | Sesión de 30 días; token en `localStorage` | `auth.test.js` |

### HU-14 Gestionar categorías
| # | Criterio | Dónde | Prueba |
|---|---|---|---|
| 1 | Crear, renombrar, desactivar | `categorias.rutas.js`, `AdminCategorias.jsx` | `categorias.test.js` |
| 2 | Con máquinas no se elimina | `eliminar()` → 409; botón deshabilitado en la UI | `categorias.test.js` |
| 3 | Desactivada no aparece en el catálogo | `GET /categorias` solo devuelve activas | `categorias.test.js` |
| 4 | Nombre único | Índice `lower(trim(nombre))` | `categorias.test.js` |
| 5 | Máquina con exactamente una categoría | `categoria_id NOT NULL` + FK | Esquema de la BD |

### HU-08 Registrar y publicar máquina
| # | Criterio | Dónde | Prueba |
|---|---|---|---|
| 1 | Datos obligatorios | Zod en `maquinas.rutas.js` + `AdminMaquinaForm.jsx` | `maquinas.test.js` |
| 2 | Hasta 5 fotos JPG/PNG | Multer + firma del archivo + trigger en BD | `maquinas.test.js` |
| 3 | Foto principal | Índice único parcial; la 1.ª foto es principal por defecto | `maquinas.test.js` |
| 4 | Guardar como borrador | Estado inicial `BORRADOR` | `maquinas.test.js` |
| 5 | Al publicar aparece de inmediato | `publicar()`; el catálogo consulta en vivo | `maquinas.test.js` |
| 6 | Editar después de publicada | `PUT /admin/maquinas/:id` | `maquinas.test.js` |
| 7 | Retirar sin borrar el historial | Estado `RETIRADA`; reservas con `ON DELETE RESTRICT` | `maquinas.test.js` |

### HU-09 Gestionar disponibilidad
| # | Criterio | Dónde | Prueba |
|---|---|---|---|
| 1 | Uno o varios rangos | `bloquear()` en una transacción | `disponibilidad.test.js` |
| 2 | Fechas bloqueadas no disponibles | `GET /maquinas/:id/disponibilidad`; en el Sprint 3, HU-05 también las valida | `disponibilidad.test.js` |
| 3 | No bloquear sobre una reserva pagada | Consulta de superposición con `daterange` | `disponibilidad.test.js` |
| 4 | Desbloquear | `DELETE .../bloqueos/:id` | `disponibilidad.test.js` |
| 5 | Se refleja de inmediato | Sin caché; consulta en vivo | `disponibilidad.test.js` |

### HU-03 Ver catálogo de maquinaria
| # | Criterio | Dónde | Prueba |
|---|---|---|---|
| 1 | Solo publicadas | `WHERE estado = 'PUBLICADA'` | `maquinas.test.js` |
| 2 | Foto, nombre, categoría, tarifa | `TarjetaMaquina` | `maquinas.test.js` |
| 3 | Paginado | `?pagina&tamanio` + componente `Paginacion` | `maquinas.test.js` |
| 4 | Visible sin sesión | Ruta pública | `maquinas.test.js` |
| 5 | Enlaza al detalle | `/maquinas/:id` | `maquinas.test.js` |

## Decisiones tomadas (para revisar con el Product Owner)
- **"Publicada y Activa" (HU-03):** una máquina es visible si su estado es `PUBLICADA`. Una máquina "en mantenimiento" sigue en el catálogo con una insignia, y en su detalle se muestra un aviso (HU-04 criterio 5).
- **Categoría desactivada:** deja de aparecer como filtro, pero sus máquinas publicadas siguen visibles. No se pueden asignar máquinas nuevas a una categoría inactiva, ni publicar una máquina cuya categoría esté inactiva.
- **Bloqueos:** no se pueden bloquear fechas pasadas ni superponer dos bloqueos. Se permite bloquear sobre una reserva *pendiente de pago*, porque el criterio solo menciona las pagadas; conviene confirmarlo antes del Sprint 3.
- **Sesión:** 30 min de inactividad y 8 h como máximo; con "Recordarme", 7 días de inactividad y 30 días como máximo. El bloqueo por intentos fallidos dura 15 min.
