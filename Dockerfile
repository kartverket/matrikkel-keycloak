# Build matrikkel extensions
FROM eclipse-temurin:25-jdk AS extensions-builder

COPY ./extensions /extensions
WORKDIR /extensions
RUN ./gradlew --no-daemon build

FROM keycloak/keycloak:26.8.0@sha256:b0f60d489d51c5d113390bdf5461d4c06e6051be026c05549f2e1e10ec352bcc AS keycloak-builder
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
ADD https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc17/23.8.0.25.04/ojdbc17-23.8.0.25.04.jar /opt/keycloak/providers/ojdbc17.jar
ADD https://repo1.maven.org/maven2/com/oracle/database/nls/orai18n/23.8.0.25.04/orai18n-23.8.0.25.04.jar /opt/keycloak/providers/orai18n.jar

USER root
RUN chmod +r-w opt/keycloak/providers/*

USER keycloak
RUN /opt/keycloak/bin/kc.sh build --spi-email-sender--oauth-email-provider--enabled=true --spi-email-sender--provider-default=oauth-email-provider

FROM keycloak/keycloak:26.8.0@sha256:b0f60d489d51c5d113390bdf5461d4c06e6051be026c05549f2e1e10ec352bcc
# SKIP runs all containers with UID 150
COPY --from=keycloak-builder --chown=150:150 /opt/keycloak/ /opt/keycloak/

ARG KC_DB=oracle
ENV KC_DB=$KC_DB
ENV TZ=Europe/Oslo
ENV KC_SPI_BRUTE_FORCE_PROTECTOR__DEFAULT_BRUTE_FORCE_DETECTOR__ALLOW_CONCURRENT_REQUESTS=true

USER 150

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]
