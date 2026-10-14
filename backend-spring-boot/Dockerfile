ARG BUILD_BASE
ARG RUNTIME_BASE

FROM ${BUILD_BASE} AS build

WORKDIR /workspace
COPY . .
RUN chmod +x mvnw \
    && ./mvnw -B -Dmaven.test.skip=true package \
    && test -s target/ethioloadai-backend-1.0.0.jar

FROM ${RUNTIME_BASE} AS runtime

WORKDIR /app
RUN mkdir -p /app/uploads-e2e \
    && chown -R 10001:10001 /app
COPY --from=build --chown=10001:10001 \
    /workspace/target/ethioloadai-backend-1.0.0.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=e2e
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
