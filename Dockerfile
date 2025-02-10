# Build matrikkel extensions
FROM eclipse-temurin:21-jdk AS extensions-builder

COPY ./extensions ./extensions
WORKDIR ./extensions

RUN ./gradlew --no-daemon build

FROM keycloak/keycloak:26.0.5@sha256:089b5898cb0ba151224c83aef3806538582880029eb5ea71c2afd00b627d4907 as keycloak-builder
ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth'


# Copy and install providers
COPY --from=extensions-builder /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar

ADD https://github.com/aerogear/keycloak-metrics-spi/releases/download/7.0.0/keycloak-metrics-spi-7.0.0.jar /opt/keycloak/providers/keycloak-metrics-spi.jar

# Adding Oracle JDBC - https://www.keycloak.org/server/db
ADD https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc11/23.5.0.24.07/ojdbc11-23.5.0.24.07.jar /opt/keycloak/providers/ojdbc11.jar
ADD https://repo1.maven.org/maven2/com/oracle/database/nls/orai18n/23.5.0.24.07/orai18n-23.5.0.24.07.jar /opt/keycloak/providers/orai18n.jar

USER root
RUN chmod +r-w opt/keycloak/providers/*

USER keycloak
RUN /opt/keycloak/bin/kc.sh --spi-email-sender-provider-oauth-email-provider-enabled=true --spi-email-sender-provider=oauth-email-provider build

FROM keycloak/keycloak:26.0.5@sha256:089b5898cb0ba151224c83aef3806538582880029eb5ea71c2afd00b627d4907
# SKIP runs all containers with UID 150
COPY --from=keycloak-builder --chown=150:150 /opt/keycloak/ /opt/keycloak/

ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    TZ=Europe/Oslo

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
