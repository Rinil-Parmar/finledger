# Stage 1: Build the backend JAR (skip tests - CI handles that)
FROM eclipse-temurin:17-jdk AS backend-build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q
COPY src src
RUN ./mvnw package -DskipTests -B -q

# Stage 2: Build the frontend
FROM node:18-alpine AS frontend-build
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend .
RUN npx vite build

# Stage 3: Runtime - slim JRE, no build tools
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=backend-build /app/target/*.jar app.jar
COPY --from=frontend-build /app/dist /app/static

ENV SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/
ENV SERVER_PORT=8080

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
