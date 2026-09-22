# MaquiRenta: sistema de alquiler de maquinaria

Proyecto del curso **Agile Development (ISIA-109, UPAO)**. Es una plataforma web para que los clientes consulten y reserven maquinaria pesada, y para que el administrador gestione la flota.

| Capa | Tecnología |
|---|---|
| Base de datos | PostgreSQL 16 |
| Backend (API REST) | Node.js 20 + Express 5, `pg`, JWT, bcrypt, Zod, Multer |
| Frontend | React 18 + Vite + React Router |
| Pruebas | Jest + Supertest (pruebas de aceptación por historia de usuario) |
| CI / Despliegue | GitHub Actions · Render |

> **Nota:** "MaquiRenta" es un nombre provisional. Se cambia en `frontend/src/utils/formato.js` (`NOMBRE_APP`) y en `frontend/index.html`.

## Estado del backlog

| Sprint | Ítem | Estado |
|---|---|---|
| 1 | EN-01 Repositorio, entorno y CI · EN-02 Base de datos · EN-03 Autenticación · EN-04 Despliegue · EN-05 Roles | ✅ Hecho |
| 2 | HU-01 Registro · HU-02 Login/logout · HU-14 Categorías · HU-08 Registrar y publicar máquina · HU-09 Disponibilidad · HU-03 Catálogo | ✅ Hecho |
| 3 | HU-11 Buscar/filtrar · HU-04 Detalle (calendario y botón Reservar) · HU-05 Reservar · HU-12 Cancelar · HU-07 Mis reservas | Pendiente |
| 4 | HU-06 Pago con Mercado Pago · HU-13 Historial de pagos · HU-10 Reservas y pagos (admin) · HU-15 Gestionar clientes | Pendiente |

Las tablas de los Sprints 3 y 4 (`reservas`, `pagos`, `solicitudes_reembolso`, `auditoria`) ya existen en la base de datos. En [`docs/trazabilidad.md`](docs/trazabilidad.md) está cada criterio de aceptación con el archivo que lo implementa y la prueba que lo verifica.

## Estructura

```
alquiler-maquinaria/
├── database/
│   ├── migrations/001_esquema_inicial.sql   ← EN-02: todas las tablas
│   └── docker-init/                          ← crea la BD de pruebas en Docker
├── backend/
│   ├── src/
│   │   ├── app.js                ← rutas y middlewares
│   │   ├── config.js, db.js
│   │   ├── middleware/           ← autenticación, roles, validación, fotos, errores
│   │   ├── modulos/              ← auth, categorias, maquinas, disponibilidad
│   │   └── utils/
│   ├── scripts/                  ← migrar.js y sembrar.js
│   └── tests/                    ← pruebas por historia de usuario
├── frontend/
│   └── src/
│       ├── api/cliente.js        ← llamadas al backend
│       ├── contexto/AuthContext  ← sesión del usuario
│       ├── componentes/
│       └── paginas/              ← públicas, cliente y admin/
├── docs/                         ← diagrama ER, API, trazabilidad
├── docker-compose.yml            ← PostgreSQL local
├── render.yaml                   ← despliegue (EN-04)
└── .github/workflows/ci.yml      ← integración continua (EN-01)
```

## Cómo ejecutarlo en tu computadora

### Requisitos
- [Node.js 20 o superior](https://nodejs.org)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) para la base de datos, o PostgreSQL instalado (ver más abajo)

### 1. Base de datos
```bash
docker compose up -d
```
Esto crea las bases `alquiler_maquinaria` (desarrollo) y `alquiler_maquinaria_test` (pruebas). El usuario y la contraseña son `alquiler` / `alquiler`.

<details>
<summary>¿Sin Docker? Con PostgreSQL instalado</summary>

En psql o pgAdmin, como superusuario:
```sql
CREATE USER alquiler WITH PASSWORD 'alquiler';
CREATE DATABASE alquiler_maquinaria OWNER alquiler;
CREATE DATABASE alquiler_maquinaria_test OWNER alquiler;
```
La migración activa la extensión `btree_gist`, que viene con PostgreSQL y que el dueño de la base puede activar desde la versión 13.
</details>

### 2. Backend
```bash
cd backend
cp .env.example .env        # en Windows: copy .env.example .env
npm install
npm run db:migrar           # crea las tablas
npm run db:sembrar          # crea el admin, 6 categorías y 7 máquinas de ejemplo
npm run dev                 # API en http://localhost:3000
```
Para comprobarlo, abre http://localhost:3000/api/salud y debe responder `{"estado":"ok"}`.

**Administrador inicial:** `admin@alquiler.pe` / `Admin12345`. Cámbialo en `.env` antes de ejecutar `db:sembrar`.

### 3. Frontend
En otra terminal:
```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```
En desarrollo, Vite redirige `/api` y `/uploads` al backend, así que no necesitas configurar nada más.

### 4. Pruebas automatizadas
```bash
cd backend
npm test                    # usa la BD alquiler_maquinaria_test (se borra en cada ejecución)
npm run test:cobertura      # con reporte de cobertura en backend/coverage/
```

## Prueba rápida (demo del Sprint 2)

1. Entra como admin, ve a **Administración → Categorías** y crea "Grúas".
2. En **Máquinas → + Registrar máquina** completa los datos y guárdala. Queda como **Borrador** y aún no se ve en el catálogo.
3. Sube 2 fotos, cambia cuál es la principal y pulsa **Publicar**. Ahora aparece en el catálogo.
4. En **Disponibilidad** bloquea dos rangos de fechas. Abre la máquina en el catálogo: esas fechas aparecen como no disponibles.
5. Cierra sesión, regístrate como cliente nuevo y entra: llegas a **Mi cuenta**. Si intentas abrir `/admin`, el sistema te lo impide.
6. Equivoca la contraseña 5 veces: la cuenta queda bloqueada 15 minutos.

## Reglas de sesión (HU-02)

| | Sin "Recordarme" | Con "Recordarme" |
|---|---|---|
| Expira por inactividad | 30 minutos | 7 días |
| Duración máxima | 8 horas | 30 días |
| Dónde se guarda el token | `sessionStorage` (se borra al cerrar la pestaña) | `localStorage` |

Cada token está ligado a una fila de la tabla `sesiones`. Al cerrar sesión, esa fila se marca como revocada y el token deja de funcionar aunque aún no haya vencido. Los valores se cambian en `backend/src/config.js`.

## Despliegue (EN-04)

1. Sube el repositorio a GitHub.
2. En [Render](https://render.com) entra a **New → Blueprint** y elige el repositorio. `render.yaml` crea la base de datos, la API y el frontend.
3. Cuando termine, completa las dos variables marcadas como `sync: false`:
   - En **alquiler-api**: `CORS_ORIGIN` = URL del frontend (p. ej. `https://alquiler-web.onrender.com`)
   - En **alquiler-web**: `VITE_API_URL` = URL de la API (p. ej. `https://alquiler-api.onrender.com`), y luego **Manual Deploy**.
4. Carga los datos iniciales una vez desde la pestaña **Shell** de la API: `cd backend && npm run db:sembrar`.

Limitaciones del plan gratuito de Render:
- La API "se duerme" tras 15 minutos sin uso y tarda unos 50 segundos en despertar. Ábrela un rato antes de exponer.
- La base de datos gratuita vence a los 30 días; se puede recrear.
- El disco no es persistente: **las fotos subidas se pierden en cada redespliegue**. Para producción conviene guardarlas en un servicio externo (Cloudinary o S3). Es buen candidato para un habilitador del Sprint 3.

## Documentación
- [Base de datos: diagrama ER y reglas](docs/base-de-datos.md)
- [API: endpoints](docs/api.md)
- [Trazabilidad: criterio → código → prueba](docs/trazabilidad.md)
