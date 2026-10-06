# MaquiRenta: sistema de alquiler de maquinaria

Proyecto del curso **Agile Development (ISIA-109, UPAO)**, 2026. Plataforma web para alquilar maquinaria pesada **por hora**: los clientes consultan el catálogo y reservan, y el administrador gestiona la flota.

- Frontend: https://agiledevelo.vercel.app
- Repositorio: https://github.com/kevinrujel26-arch/agiledevelo

## Tecnologías

| Capa | Tecnología |
|---|---|
| Base de datos | PostgreSQL 16 (Neon en producción, Docker en local) |
| Backend (API REST) | Java 21 + **Spring Boot 3.3.5** + **Spring Security** (sesión con JWT y roles) |
| Frontend | React 18 + Vite + React Router, CSS propio (modo día/noche) |
| Fotos | Cloudinary |
| Pruebas | JUnit 5 + Spring Boot Test (114 pruebas) |
| Construcción | Maven |
| CI / Despliegue | GitHub Actions · Render (backend, Docker) · Vercel (frontend) · Neon (BD) |

## Funciones actuales

**Clientes**
- Registro con validación en vivo (nombre, correo, contraseña con requisitos y celular de 9 dígitos).
- Inicio de sesión con correo y contraseña, o con **Google** ("Continuar con Google").
- Catálogo de máquinas publicadas con búsqueda en vivo (nombre o marca), filtros por categoría y rango de tarifa por hora, paginación.
- Detalle de máquina: fotos, especificaciones, ubicación, horas de uso, tarifa por hora, calendario de disponibilidad y botón **Reservar** (pide iniciar sesión).
- Mi cuenta (con aviso para completar el celular si entraste con Google).
- Modo día / noche.

**Administrador** (`/admin`)
- Crear, editar, retirar y publicar máquinas; hasta 5 fotos con una principal; estados borrador / publicada.
- No se puede publicar sin tarifa, categoría ni foto principal.

Los roles son `CLIENTE` y `ADMINISTRADOR`; el acceso se valida en el backend.

## Estado del backlog

| Sprint | Alcance | Estado |
|---|---|---|
| 1 | Registro, login, catálogo, búsqueda y detalle de máquina | ✅ Hecho |
| 2 | Panel de administración (HU-06), reservar (HU-07), pagar (HU-08) | En curso |
| 3 y 4 | Ver el Sprint Backlog del equipo | Pendiente |

## Estructura del repositorio

```
alquiler-maquinaria/
├── backend/       API Spring Boot (código en src/main/java/alquiler, pruebas en src/test)
├── frontend/      Aplicación React + Vite
├── database/      Migraciones SQL (database/migrations/001 … 007)
├── docs/          Documentación (API y base de datos)
├── docker-compose.yml   PostgreSQL local
└── Dockerfile     Imagen del backend para Render
```

## Cómo ejecutarlo en tu computadora

### Requisitos
- **JDK 21** y **Maven**
- **Node.js 20+**
- **Docker Desktop** (base de datos local)

### 1. Base de datos
```bash
docker compose up -d
```
Crea `alquiler_maquinaria` (desarrollo) y `alquiler_maquinaria_test` (pruebas) en el puerto **5433**, con usuario y contraseña `alquiler`.

### 2. Backend
```bash
cd backend
copy .env.example .env      # Mac/Linux: cp .env.example .env
mvn spring-boot:run         # API en http://localhost:3000
```
Edita `backend/.env` con tus valores (base de datos, `JWT_SECRET`, Cloudinary, Google). Las migraciones se aplican solas al arrancar. Comprueba la API en http://localhost:3000/api/salud.

El administrador inicial se define en `backend/.env` (`ADMIN_CORREO`, `ADMIN_CONTRASENA`) y se crea con el comando de sembrado. Para convertir a otro usuario en administrador:
```sql
UPDATE usuarios SET rol = 'ADMINISTRADOR' WHERE correo = 'correo@ejemplo.com';
```

### 3. Frontend
```bash
cd frontend
copy .env.example .env      # Mac/Linux: cp .env.example .env
npm install
npm run dev                 # http://localhost:5173
```

### 4. Pruebas
```bash
mvn -f backend/pom.xml verify    # usa alquiler_maquinaria_test (se borra en cada ejecución)
npm run build --prefix frontend  # compila el frontend
```
Con Docker encendido deben pasar las **114 pruebas**.

## Variables de entorno

Los archivos `.env` reales **no se suben al repositorio**; solo los `.env.example`.

**Backend** (`backend/.env` y Render)

| Variable | Descripción |
|---|---|
| `DATABASE_URL` | URL de PostgreSQL |
| `DB_SSL` | `true` en la nube (Neon), `false` en local |
| `JWT_SECRET` | Clave larga y aleatoria (mínimo 16 caracteres) |
| `CORS_ORIGIN` | URL del frontend, sin `/` final (varias separadas por coma) |
| `APP_TZ` | Zona horaria, `America/Lima` |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Almacenamiento de fotos |
| `GOOGLE_CLIENT_ID` | Client ID de Google (opcional) |

**Frontend** (`frontend/.env` y Vercel)

| Variable | Descripción |
|---|---|
| `VITE_API_URL` | URL del backend en producción (vacío en local) |
| `VITE_GOOGLE_CLIENT_ID` | Mismo Client ID de Google (opcional) |

## Inicio de sesión con Google

Usa Google Identity Services: el botón entrega un token al frontend, que lo envía a `POST /api/auth/google`; el backend lo verifica con Google y abre la misma sesión que el login normal.

- Si el correo ya existe, se **vincula** a esa cuenta (conserva rol, nombre y celular).
- Si es nuevo, se crea como `CLIENTE` sin contraseña y se pide completar el celular en Mi cuenta (`PATCH /api/auth/yo`).
- Nunca se crea un administrador por Google.
- Una cuenta creada solo con Google no puede entrar con contraseña.

**Configuración**
1. En [Google Cloud Console](https://console.cloud.google.com) crea un proyecto → *Google Auth Platform* → configura la pantalla de consentimiento como **Externo**.
2. En *Clientes* crea un ID de cliente de tipo **Aplicación web**. En *Orígenes autorizados de JavaScript* agrega `http://localhost:5173`, `http://localhost` y la URL de Vercel (sin `/` final). No necesitas URI de redirección ni Client Secret.
3. Mientras la app esté en modo *Prueba*, agrega los correos permitidos en *Público → Usuarios de prueba*.
4. Pon el mismo Client ID en `GOOGLE_CLIENT_ID` (backend y Render) y en `VITE_GOOGLE_CLIENT_ID` (frontend y Vercel). En Vercel hay que **volver a desplegar** tras cambiar la variable.

Sin estas variables el botón no aparece y todo lo demás funciona igual.

## Sesión

El inicio de sesión devuelve un **JWT** en la cabecera `Authorization`, ligado a la tabla `sesiones`. Al cerrar sesión se revoca y deja de funcionar aunque no haya vencido.

## Despliegue

| Parte | Servicio | Cómo |
|---|---|---|
| Base de datos | **Neon** | Crea un proyecto y copia la connection string |
| Backend | **Render** (Web Service, Docker) | Usa el `Dockerfile` de la raíz; configura las variables de arriba |
| Frontend | **Vercel** | Root Directory `frontend`; ya incluye `vercel.json` |

Flujo de trabajo: rama → push → GitHub Actions en verde → merge a `main` → Render y Vercel despliegan solos. Las migraciones se aplican al arrancar el backend.

Limitación del plan gratuito de Render: la API se duerme tras 15 minutos sin uso y tarda cerca de 1 minuto en despertar.

## Documentación
- [Base de datos](docs/base-de-datos.md)
- [API: endpoints](docs/api.md)