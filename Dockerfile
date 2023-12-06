# Build matrikkel extensions
FROM eclipse-temurin:17-jdk-jammy AS extensions-builder

COPY ./extensions ./extensions
WORKDIR ./extensions

RUN ./gradlew --no-daemon build

# Fetch Keycloak metrics SPI
FROM curlimages/curl:8.5.0 as metrics-spi-builder

ARG KEYCLOAK_METRICS_SPI_RELEASE=4.0.0
RUN curl -f -L https://github.com/aerogear/keycloak-metrics-spi/releases/download/${KEYCLOAK_METRICS_SPI_RELEASE}/keycloak-metrics-spi-${KEYCLOAK_METRICS_SPI_RELEASE}.jar  \
    --output /tmp/keycloak-metrics-spi.jar

FROM quay.io/keycloak/keycloak:22.0.5 as keycloak-builder

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth'

WORKDIR /opt/keycloak

# Copy and install providers
COPY --from=extensions-builder --chown=keycloak:keycloak /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder --chown=keycloak:keycloak /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar
COPY --from=metrics-spi-builder --chown=keycloak:keycloak /tmp/keycloak-metrics-spi.jar /opt/keycloak/providers/keycloak-metrics-spi.jar

RUN /opt/keycloak/bin/kc.sh --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider build

FROM quay.io/keycloak/keycloak:22.0.5
# SKIP runs all containers with UID 150
COPY --from=keycloak-builder --chown=150:150 /opt/keycloak/ /opt/keycloak/

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    TZ=Europe/Oslo

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
