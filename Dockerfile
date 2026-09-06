FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV PORT=8080
EXPOSE 8080

CMD ["sh", "-c", "if [ -n \"$DATABASE_URL\" ] && [ \"${DATABASE_URL#jdbc:}\" = \"$DATABASE_URL\" ]; then export SPRING_DATASOURCE_URL=\"jdbc:$DATABASE_URL\"; fi; exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
