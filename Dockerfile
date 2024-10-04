# Build matrikkel extensions
FROM eclipse-temurin:17-jdk AS extensions-builder

COPY ./extensions ./extensions
WORKDIR ./extensions

RUN ./gradlew --no-daemon build

FROM quay.io/keycloak/keycloak:24.0.4@sha256:ff02c932f0249c58f32b8ff1b188a48cc90809779a3a05931ab67f5672400ad0 as keycloak-builder

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth'


# Copy and install providers
COPY --from=extensions-builder /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar

ADD https://github.com/aerogear/keycloak-metrics-spi/releases/download/5.0.0/keycloak-metrics-spi-5.0.0.jar /opt/keycloak/providers/keycloak-metrics-spi.jar

USER root
RUN chmod +r-w opt/keycloak/providers/*

USER keycloak
RUN /opt/keycloak/bin/kc.sh --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider build

FROM quay.io/keycloak/keycloak:24.0.4@sha256:ff02c932f0249c58f32b8ff1b188a48cc90809779a3a05931ab67f5672400ad0
# SKIP runs all containers with UID 150
COPY --from=keycloak-builder --chown=150:150 /opt/keycloak/ /opt/keycloak/

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    TZ=Europe/Oslo

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
