-- Se ejecuta solo la primera vez que arranca el contenedor de PostgreSQL.
-- Crea la base de datos que usan las pruebas automatizadas (npm test).
CREATE DATABASE alquiler_maquinaria_test OWNER alquiler;
