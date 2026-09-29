# API REST (Sprints 1 y 2)

Base: `http://localhost:3000/api`. Todas las respuestas son JSON. Backend en **Java 21 sin frameworks** (`com.sun.net.httpserver` del JDK).

**Autenticación:** cabecera `Authorization: Bearer <token>`. El token se obtiene en `POST /auth/login`.

**Roles:** solo existen `CLIENTE` y `ADMINISTRADOR` (las máquinas son del negocio; no hay proveedores).

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
| POST | `/auth/registro` | Público | `{ nombre, correo, telefono, contrasena }` → 201 `{ mensaje, usuario }`. El rol siempre es CLIENTE. `telefono` es obligatorio (ver abajo) |
| POST | `/auth/login` | Público | `{ correo, contrasena, recordarme? }` → `{ token, expiraEn, usuario }` |
| POST | `/auth/logout` | Con sesión | Invalida el token → 204 |
| GET | `/auth/yo` | Con sesión | `{ usuario }` |

`usuario` (en registro, login y `/auth/yo`): `{ id, nombre, correo, telefono, rol }`. `telefono` puede ser `null` en cuentas creadas antes de que se pidiera el celular.

**Celular (`telefono`, HU-02):** celular peruano de 9 dígitos que empieza con 9. Se aceptan espacios, guiones y el prefijo `+51` o `51` (ej. `"+51 987-654-321"`), y se guarda normalizado (`"987654321"`). Si falta o es inválido → 400 con `detalles: [{ campo: "telefono", mensaje: "Ingresa un celular válido de 9 dígitos que empiece con 9" }]` (o `"El celular es obligatorio"` si no se envía).

## Catálogo público (HU-03, HU-06, HU-14, HU-09)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/maquinas?pagina=1&tamanio=12&categoriaId=&q=&precioMin=&precioMax=` | Máquinas publicadas, paginadas → `{ datos, paginacion }`. Todos los filtros son opcionales y se combinan |
| GET | `/maquinas/:id` | Detalle de una máquina publicada (fotos, especificaciones) |
| GET | `/maquinas/:id/disponibilidad?desde=&hasta=` | Fechas ocupadas (bloqueos y reservas). Por defecto, los próximos 180 días |
| GET | `/categorias` | Categorías activas |

**Filtros del catálogo:**
- `categoriaId`: solo esa categoría.
- `q`: palabra clave en el nombre o la marca.
- `precioMin` / `precioMax` (HU-06): rango de **tarifa por hora** en soles, decimales ≥ 0, inclusivo en ambos extremos (`precioMin=100&precioMax=150` incluye las de S/ 100 y S/ 150). Se puede enviar solo uno. Si `precioMin > precioMax` → 400 con `detalles: [{ campo: "precioMin", mensaje: "El precio mínimo no puede ser mayor que el precio máximo" }]`. Un valor negativo o que no es número → 400.
- `paginacion.total` y `totalPaginas` cuentan solo las máquinas que cumplen los filtros.

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
| GET | `/admin/maquinas?estado=&q=&categoriaId=&precioMin=&precioMax=&pagina=` | Toda la flota (mismos filtros que el catálogo, más `estado`) |
| GET | `/admin/maquinas/:id` | Detalle con fotos |
| POST | `/admin/maquinas` | Crea en estado BORRADOR. Cuerpo: `{ categoriaId, nombre, marca, modelo, tarifaHoraria, ubicacion, descripcion?, especificaciones?: { "Potencia": "146 HP" }, enMantenimiento? }` |
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
# 1. Iniciar sesión y copiar el "token" de la respuesta
curl -s -X POST localhost:3000/api/auth/login -H "Content-Type: application/json" \
  -d '{"correo":"admin@alquiler.pe","contrasena":"Admin12345"}'

# 2. Usarlo en las rutas de administrador
curl -s localhost:3000/api/admin/maquinas -H "Authorization: Bearer PEGA_AQUI_EL_TOKEN"
curl -s -X POST localhost:3000/api/admin/maquinas/1/fotos -H "Authorization: Bearer PEGA_AQUI_EL_TOKEN" -F "fotos=@excavadora.jpg"
```

## Dónde está cada ruta en el código
Cada módulo registra sus rutas en su controlador (`backend/src/main/java/pe/upao/alquiler/...`):

| Rutas | Controlador |
|---|---|
| `/api/auth/*` | `auth/AuthControlador.java` |
| `/api/categorias`, `/api/admin/categorias/*` | `categorias/CategoriaControlador.java` |
| `/api/maquinas`, `/api/admin/maquinas/*` (incluye fotos) | `maquinas/MaquinaControlador.java` |
| `/api/maquinas/{id}/disponibilidad`, `/api/admin/maquinas/{id}/bloqueos/*` | `disponibilidad/DisponibilidadControlador.java` |
| `/api/salud` | `Aplicacion.java` |
