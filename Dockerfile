# Official images. For a controlled release, pin reviewed image digests in your deployment.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY repository-core/pom.xml repository-core/pom.xml
COPY repository-server/pom.xml repository-server/pom.xml
COPY repository-core/src repository-core/src
COPY repository-server/src repository-server/src
COPY deploy/HealthProbe.java deploy/HealthProbe.java
# Builds and runs both core and Spring/Tomcat tests; failures stop the image build.
RUN mvn -B -ntp clean verify && javac --release 25 -d /healthcheck deploy/HealthProbe.java

FROM eclipse-temurin:25-jre
RUN groupadd --gid 10001 graph && useradd --uid 10001 --gid 10001 --create-home graph \
    && mkdir -p /app /var/lib/graph-repository && chown -R 10001:10001 /app /var/lib/graph-repository
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/repository-server/target/graph-repository.jar /app/app.jar
COPY --from=build --chown=10001:10001 /healthcheck /app/healthcheck
ENV GR_HOME=/var/lib/graph-repository GR_BIND=0.0.0.0 GR_PORT=8081
USER 10001:10001
EXPOSE 8081
VOLUME ["/var/lib/graph-repository"]
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 CMD ["java", "-cp", "/app/healthcheck", "HealthProbe"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
