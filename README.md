# MaquiRenta: sistema de alquiler de maquinaria

Proyecto del curso **Agile Development (ISIA-109, UPAO)**. Es una plataforma web para que los clientes consulten y reserven maquinaria pesada, y para que el administrador gestione la flota.

| Capa | Tecnología |
|---|---|
| Base de datos | PostgreSQL 16 |
| Backend (API REST) | **Java 21 sin frameworks**: servidor HTTP del JDK (`com.sun.net.httpserver`) y JDBC |
| Frontend | React 18 + Vite + React Router |
| Pruebas | JUnit 5 (pruebas de aceptación por historia de usuario) |
| Construcción | Maven |
| CI / Despliegue | GitHub Actions · Render (backend) · Vercel (frontend) · Neon (BD) |

Las **únicas dependencias** del backend son el driver JDBC de PostgreSQL y JUnit. El resto está escrito en Java con el JDK: el enrutador, el JSON, los tokens JWT, el cifrado de contraseñas (PBKDF2) y la subida de archivos (multipart).

> **Nota:** "MaquiRenta" es un nombre provisional. Se cambia en `frontend/src/utils/formato.js` (`NOMBRE_APP`) y en `frontend/index.html`.

## Estado del backlog

| Sprint | Ítem | Estado |
|---|---|---|
| 1 | EN-01 Repositorio y CI · EN-02 Base de datos · EN-03 Autenticación · EN-04 Despliegue · EN-05 Roles | ✅ Hecho |
| 2 | HU-01 Registro · HU-02 Login/logout · HU-14 Categorías · HU-08 Registrar y publicar máquina · HU-09 Disponibilidad · HU-03 Catálogo | ✅ Hecho |
| 3 | HU-11 Buscar/filtrar · HU-04 Detalle (calendario y botón Reservar) · HU-05 Reservar · HU-12 Cancelar · HU-07 Mis reservas | Pendiente |
| 4 | HU-06 Pago con Mercado Pago · HU-13 Historial de pagos · HU-10 Reservas y pagos (admin) · HU-15 Gestionar clientes | Pendiente |

En [`docs/trazabilidad.md`](docs/trazabilidad.md) está cada criterio de aceptación con la clase que lo implementa y la prueba que lo verifica.

## Arquitectura del backend

```
Controlador (HTTP)  ->  Servicio (reglas de negocio)  ->  Repositorio (SQL/JDBC)  ->  PostgreSQL
```

```
backend/src/main/java/pe/upao/alquiler/
├── App.java                 <- punto de entrada (servidor, migrar, sembrar, reiniciar)
├── Aplicacion.java          <- arma todos los objetos (inyección de dependencias a mano)
├── config/Config.java       <- lee variables de entorno y backend/.env
├── http/                    <- Servidor, Enrutador, Solicitud, Respuesta, Multipart
├── json/Json.java           <- lector/escritor de JSON
├── bd/                      <- Bd (JDBC + pool), Migraciones, Sembrador
├── seguridad/               <- Jwt, Contrasenas (PBKDF2), Autenticador, Rol
├── util/                    <- Validador, Fechas, Paginacion, ErrorApp
├── modelo/                  <- Categoria, Maquina, Foto, Bloqueo (records)
├── auth/                    <- HU-01, HU-02
├── categorias/              <- HU-14
├── maquinas/                <- HU-03, HU-08 (incluye fotos)
└── disponibilidad/          <- HU-09
```

## Cómo ejecutarlo en tu computadora

### Requisitos
- **JDK 21**, por ejemplo [Temurin 21](https://adoptium.net/temurin/releases/?version=21).
- **Maven**. IntelliJ IDEA y NetBeans ya lo traen integrado. Para usarlo en la terminal: [descárgalo](https://maven.apache.org/download.cgi), descomprímelo y agrega su carpeta `bin` al PATH.
- **Node.js 20+**, para el frontend.
- **Docker Desktop**, para la base de datos local.

### 1. Base de datos
```bash
docker compose up -d
```
Crea las bases `alquiler_maquinaria` (desarrollo) y `alquiler_maquinaria_test` (pruebas) en el puerto **5433**, con usuario y contraseña `alquiler` / `alquiler`.

### 2. Backend
```bash
cd backend
copy .env.example .env               # en Mac/Linux: cp .env.example .env
mvn compile exec:java "-Dexec.args=reiniciar"   # crea las tablas y carga admin, categorías y máquinas
mvn compile exec:java                            # arranca la API en http://localhost:3000
```
Para comprobarlo, abre http://localhost:3000/api/salud y debe responder `{"estado":"ok"}`.

**Administrador inicial:** `admin@alquiler.pe` / `Admin12345`. Se configura en `.env`.

Otros comandos útiles:
| Comando | Qué hace |
|---|---|
| `mvn compile exec:java "-Dexec.args=migrar"` | Aplica solo las migraciones pendientes |
| `mvn compile exec:java "-Dexec.args=sembrar"` | Carga los datos iniciales sin borrar nada |
| `mvn compile exec:java "-Dexec.args=reiniciar"` | ⚠ Borra todo y vuelve a crear la BD (solo en desarrollo) |
| `mvn package` | Genera `target/alquiler-backend.jar`, que se ejecuta con `java -jar target/alquiler-backend.jar` |

**Con IntelliJ o NetBeans:** abre la carpeta `backend` como proyecto Maven y ejecuta la clase `App`. Para cargar los datos, ejecútala con el argumento `reiniciar`. El directorio de trabajo debe ser la carpeta `backend`.

### 3. Frontend
En otra terminal:
```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```

### 4. Pruebas automatizadas
```bash
cd backend
mvn test                    # usa la BD alquiler_maquinaria_test (se borra en cada ejecución)
```
Deben pasar **50 pruebas**. En IntelliJ también puedes hacer clic derecho en `src/test/java` y elegir **Run 'All Tests'**.

## Reglas de sesión (HU-02)

| | Sin "Recordarme" | Con "Recordarme" |
|---|---|---|
| Expira por inactividad | 30 minutos | 7 días |
| Duración máxima | 8 horas | 30 días |

Cada token JWT está ligado a una fila de la tabla `sesiones`. Al cerrar sesión, esa fila se marca como revocada y el token deja de funcionar aunque aún no haya vencido. Los valores se cambian en `config/Config.java`.

## Despliegue (EN-04)

| Parte | Servicio | Cómo |
|---|---|---|
| Base de datos | **Neon** (gratis, no vence) | Crea un proyecto y copia la connection string, quitando `&channel_binding=require` |
| Backend | **Render**, Web Service con **Docker** | Usa el `Dockerfile` de la raíz del repositorio |
| Frontend | **Vercel** | Root Directory `frontend`; ya incluye `vercel.json` |

Variables del backend en Render:
| Variable | Valor |
|---|---|
| `DATABASE_URL` | la URL de Neon |
| `DB_SSL` | `true` |
| `JWT_SECRET` | una clave larga y aleatoria |
| `CORS_ORIGIN` | la URL de Vercel, sin `/` al final |
| `APP_TZ` | `America/Lima` |

En Vercel, define la variable `VITE_API_URL` con la URL de Render. Las tablas se crean solas al arrancar la API. Para cargar el admin y los datos de ejemplo en Neon, ejecuta desde tu PC `mvn compile exec:java "-Dexec.args=sembrar"` con `DATABASE_URL` y `DB_SSL=true` apuntando a Neon.

Limitaciones del plan gratuito de Render:
- La API se duerme tras 15 minutos sin uso y tarda cerca de 1 minuto en despertar. Ábrela un rato antes de exponer.
- Las fotos subidas se borran en cada redespliegue. Guardarlas en un servicio externo como Cloudinary es un buen habilitador para el Sprint 3.

## Documentación
- [Base de datos: diagrama ER y reglas](docs/base-de-datos.md)
- [API: endpoints](docs/api.md)
- [Trazabilidad: criterio → código → prueba](docs/trazabilidad.md)
