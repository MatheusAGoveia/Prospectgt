FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir /app/data
COPY --from=build /workspace/target/prospectgt-0.1.0.jar /app/prospectgt.jar
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/prospectgt.jar"]
