FROM maven:3.8-openjdk-8 AS build
WORKDIR /app
COPY vuln-app/pom.xml .
RUN mvn dependency:go-offline -q
COPY vuln-app/src ./src
RUN mvn package -q -DskipTests

FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=build /app/target/ghost-bits-vuln-1.0.0.jar app.jar
COPY vuln-app/files ./files/
COPY vuln-app/sensitive.txt ./sensitive.txt
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
