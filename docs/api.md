# API REST (Sprints 1 y 2)

Base: `http://localhost:3000/api`. Todas las respuestas son JSON.

**Autenticación:** cabecera `Authorization: Bearer <token>`. El token se obtiene en `POST /auth/login`.

**Errores:** `{ "error": "mensaje para el usuario", "detalles": [{ "campo": "correo", "mensaje": "..." }] }`

| Código | Significado |
|---|---|
| 400 | Datos inválidos |
| 401 | Sin sesión o sesión expirada |
| 403 | Sin permiso (rol incorrecto o cuenta desactivada) |
| 404 | No existe |
| 409 | Conflicto (duplicado, fechas ocupadas, etc.) |
| 423 | Cuenta bloqueada temporalmente |

## Autenticación (EN-03, HU-01, HU-02)

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/auth/registro` | Público | `{ nombre, correo, contrasena }` → 201 `{ mensaje, usuario }`. El rol siempre es CLIENTE |
| POST | `/auth/login` | Público | `{ correo, contrasena, recordarme? }` → `{ token, expiraEn, usuario }` |
| POST | `/auth/logout` | Con sesión | Invalida el token → 204 |
| GET | `/auth/yo` | Con sesión | `{ usuario }` |

## Catálogo público (HU-03, HU-14, HU-09)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/maquinas?pagina=1&tamanio=12` | Máquinas publicadas, paginadas → `{ datos, paginacion }` |
| GET | `/maquinas/:id` | Detalle de una máquina publicada (fotos, especificaciones) |
| GET | `/maquinas/:id/disponibilidad?desde=&hasta=` | Fechas ocupadas (bloqueos y reservas). Por defecto, los próximos 180 días |
| GET | `/categorias` | Categorías activas |

## Administrador (EN-05): requiere rol ADMINISTRADOR

### Categorías (HU-14)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/admin/categorias` | Todas, con `totalMaquinas` y `maquinasPublicadas` |
| POST | `/admin/categorias` | `{ nombre, descripcion? }` |
| PUT | `/admin/categorias/:id` | Renombrar o cambiar la descripción |
| PATCH | `/admin/categorias/:id/estado` | `{ activa: true \| false }` |
| DELETE | `/admin/categorias/:id` | Solo si no tiene máquinas (si tiene, responde 409) |

### Máquinas (HU-08)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/admin/maquinas?estado=&q=&categoriaId=&pagina=` | Toda la flota |
| GET | `/admin/maquinas/:id` | Detalle con fotos |
| POST | `/admin/maquinas` | Crea en estado BORRADOR. Cuerpo: `{ categoriaId, nombre, marca, modelo, tarifaDiaria, ubicacion, descripcion?, especificaciones?: { "Potencia": "146 HP" }, enMantenimiento? }` |
| PUT | `/admin/maquinas/:id` | Edita cualquier campo (también si ya está publicada) |
| POST | `/admin/maquinas/:id/publicar` | Requiere foto principal |
| POST | `/admin/maquinas/:id/retirar` | Sale del catálogo y conserva su historial |
| DELETE | `/admin/maquinas/:id` | Solo borradores sin reservas |
| POST | `/admin/maquinas/:id/fotos` | `multipart/form-data`, campo `fotos` (1 a 5 archivos JPG/PNG de hasta 5 MB) |
| PATCH | `/admin/maquinas/:id/fotos/:fotoId/principal` | Marca la foto principal |
| DELETE | `/admin/maquinas/:id/fotos/:fotoId` | Elimina una foto |

### Disponibilidad (HU-09)
| Método | Ruta | Descripción |
|---|---|---|
| GET | `/admin/maquinas/:id/bloqueos?incluirPasados=false` | Bloqueos desde hoy en adelante |
| POST | `/admin/maquinas/:id/bloqueos` | `{ rangos: [{ fechaInicio, fechaFin }], motivo? }`. Se crean todos o ninguno |
| DELETE | `/admin/maquinas/:id/bloqueos/:bloqueoId` | Desbloquea |

Las fechas usan el formato `AAAA-MM-DD` y los rangos son **inclusivos**: del `2026-10-01` al `2026-10-01` es 1 día.

## Ejemplo con curl
```bash
TOKEN=$(curl -s -X POST localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"correo":"admin@alquiler.pe","contrasena":"Admin12345"}' | node -pe 'JSON.parse(require("fs").readFileSync(0)).token')

curl -s localhost:3000/api/admin/maquinas -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:3000/api/admin/maquinas/1/fotos -H "Authorization: Bearer $TOKEN" -F "fotos=@excavadora.jpg"
```
