FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package
COPY deploy/catalog.zip /tmp/catalog.zip
RUN mkdir /workspace/catalog && cd /workspace/catalog && jar -xf /tmp/catalog.zip

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir /app/data /app/catalog
COPY --from=build /workspace/target/prospectgt-0.1.0.jar /app/prospectgt.jar
COPY --from=build /workspace/catalog/ /app/catalog/
RUN chmod -R a-w /app/catalog
ENV SERVER_ADDRESS=0.0.0.0 CATALOG_PATH=/app/catalog/catalogo-osm.jsonl
ENV JAVA_TOOL_OPTIONS="-Xms32m -Xmx256m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=48m -Xss256k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/prospectgt.jar"]
