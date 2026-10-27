# Build matrikkel extensions
FROM eclipse-temurin:25-jdk AS extensions-builder

COPY ./extensions /extensions
WORKDIR /extensions
RUN ./gradlew --no-daemon build

# Download JARs in a separate stage to work around ADD permission issues
FROM alpine@sha256:be2656061b536c1883c4034790e21d0a09c5aaaf51968b89368a3c0371ea2c9a AS jar-downloader

# Download Oracle JDBC - https://www.keycloak.org/server/db
RUN wget -O /ojdbc17.jar \
      https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc17/23.8.0.25.04/ojdbc17-23.8.0.25.04.jar && \
    wget -O /orai18n.jar \
      https://repo1.maven.org/maven2/com/oracle/database/nls/orai18n/23.8.0.25.04/orai18n-23.8.0.25.04.jar && \
    wget -O /keycloak-metrics-spi.jar \
      https://github.com/aerogear/keycloak-metrics-spi/releases/download/7.0.0/keycloak-metrics-spi-7.0.0.jar


FROM dhi.io/keycloak:26@sha256:56956e02ba899e6f9a8ed4bf67755f8906194db76be92b22e4d01fe059169f74
ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth' \
    TZ=Europe/Oslo

# Copy providers with correct ownership for the keycloak non-root user (uid 65532)
COPY --from=extensions-builder --chown=65532:65532 /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder --chown=65532:65532 /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar

COPY --from=jar-downloader --chown=65532:65532 /keycloak-metrics-spi.jar /opt/keycloak/providers/keycloak-metrics-spi.jar
COPY --from=jar-downloader --chown=65532:65532 /ojdbc17.jar /opt/keycloak/providers/ojdbc17.jar
COPY --from=jar-downloader --chown=65532:65532 /orai18n.jar /opt/keycloak/providers/orai18n.jar

RUN /opt/keycloak/bin/kc.sh build --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
