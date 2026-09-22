# EN-04: imagen del BACKEND (Java) para desplegar en Render u otro servicio con Docker.
# Se construye desde la raíz del repositorio porque necesita database/migrations.

# 1) Compilar con Maven
FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -q -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
RUN mvn -B -q -f backend/pom.xml package -DskipTests

# 2) Imagen final: solo el JRE y el .jar
FROM eclipse-temurin:21-jre
WORKDIR /app/backend
COPY --from=compilacion /app/backend/target/alquiler-backend.jar app.jar
COPY database /app/database
ENV APP_ENV=production
EXPOSE 3000
# Render define la variable PORT; la API la usa automáticamente
CMD ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
