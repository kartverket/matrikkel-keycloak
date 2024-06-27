ARG KEYCLOAK_VERSION=25.0.1

# Build matrikkel extensions
FROM eclipse-temurin:21-jdk AS extensions-builder

COPY ./extensions ./extensions
WORKDIR ./extensions

RUN ./gradlew --no-daemon build

FROM quay.io/keycloak/keycloak:$KEYCLOAK_VERSION as keycloak-builder

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth'


# Copy and install providers
COPY --from=extensions-builder /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar

ARG KEYCLOAK_METRICS_SPI_RELEASE=5.0.0
ADD https://github.com/aerogear/keycloak-metrics-spi/releases/download/${KEYCLOAK_METRICS_SPI_RELEASE}/keycloak-metrics-spi-${KEYCLOAK_METRICS_SPI_RELEASE}.jar /opt/keycloak/providers/keycloak-metrics-spi.jar

USER root
RUN chmod +r-w opt/keycloak/providers/*

USER keycloak
RUN /opt/keycloak/bin/kc.sh --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider build

FROM quay.io/keycloak/keycloak:$KEYCLOAK_VERSION
# SKIP runs all containers with UID 150
COPY --from=keycloak-builder --chown=150:150 /opt/keycloak/ /opt/keycloak/

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    TZ=Europe/Oslo

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
