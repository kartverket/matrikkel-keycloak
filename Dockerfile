# Build matrikkel extensions
FROM eclipse-temurin:25-jdk AS extensions-builder

COPY ./extensions /extensions
WORKDIR /extensions
RUN ./gradlew --no-daemon build

# Download JARs in a separate stage to work around ADD permission issues
FROM alpine@sha256:25109184c71bdad752c8312a8623239686a9a2071e8825f20acb8f2198c3f659 AS jar-downloader

# Download Oracle JDBC - https://www.keycloak.org/server/db
RUN wget -O /ojdbc17.jar \
      https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc17/23.8.0.25.04/ojdbc17-23.8.0.25.04.jar && \
    wget -O /orai18n.jar \
      https://repo1.maven.org/maven2/com/oracle/database/nls/orai18n/23.8.0.25.04/orai18n-23.8.0.25.04.jar && \
    wget -O /keycloak-metrics-spi.jar \
      https://github.com/aerogear/keycloak-metrics-spi/releases/download/7.0.0/keycloak-metrics-spi-7.0.0.jar


FROM ghcr.io/kartverket/emudhra-images/keycloak/dhi:26@sha256:7ec228edab34f6938db5d6f720dc601f873be9d6ce17fe7546ba8797854786a8
ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth' \
    TZ=Europe/Oslo \
    KC_SPI_BRUTE_FORCE_PROTECTOR__DEFAULT_BRUTE_FORCE_DETECTOR__ALLOW_CONCURRENT_REQUESTS=true

# Copy providers with correct ownership for the keycloak non-root user (uid 65532)
COPY --from=extensions-builder --chown=65532:65532 /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder --chown=65532:65532 /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar

COPY --from=jar-downloader --chown=65532:65532 /keycloak-metrics-spi.jar /opt/keycloak/providers/keycloak-metrics-spi.jar
COPY --from=jar-downloader --chown=65532:65532 /ojdbc17.jar /opt/keycloak/providers/ojdbc17.jar
COPY --from=jar-downloader --chown=65532:65532 /orai18n.jar /opt/keycloak/providers/orai18n.jar

RUN /opt/keycloak/bin/kc.sh build --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
