# Build matrikkel extensions
FROM eclipse-temurin:21-jdk AS extensions-builder

COPY ./extensions /extensions
WORKDIR /extensions
RUN ./gradlew --no-daemon build


FROM dhi.io/keycloak:26@sha256:c074118562ec2ec5596430150aae10fdd9d272ce3c2b924596b1e2d31955dadf
ARG KC_DB=oracle
ENV KC_DB=$KC_DB \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true \
    KC_HTTP_RELATIVE_PATH='/auth' \
    TZ=Europe/Oslo

# Adding Oracle JDBC - https://www.keycloak.org/server/db
RUN curl -fsSL -o /opt/keycloak/providers/ojdbc17.jar \
      https://repo1.maven.org/maven2/com/oracle/database/jdbc/ojdbc17/23.8.0.25.04/ojdbc17-23.8.0.25.04.jar && \
    curl -fsSL -o /opt/keycloak/providers/orai18n.jar \
      https://repo1.maven.org/maven2/com/oracle/database/nls/orai18n/23.8.0.25.04/orai18n-23.8.0.25.04.jar && \
    curl -fsSL -o /opt/keycloak/providers/keycloak-metrics-spi.jar \
      https://github.com/aerogear/keycloak-metrics-spi/releases/download/7.0.0/keycloak-metrics-spi-7.0.0.jar \

# Copy providers with correct ownership for the keycloak non-root user (uid 65532)
COPY --from=extensions-builder --chown=65532:65532 /extensions/build/libs/matrikkel-keycloak-extension-all.jar /opt/keycloak/providers/matrikkel-keycloak-extension.jar
COPY --from=extensions-builder --chown=65532:65532 /extensions/build/libs/matrikkel-keycloak-extension-themes.jar /opt/keycloak/providers/themes.jar

RUN /opt/keycloak/bin/kc.sh \
    --spi-email-sender-provider-oauth-email-provider-enabled=true \
    --spi-email-sender-provider=oauth-email-provider \
    build

ENTRYPOINT ["/opt/keycloak/bin/kc.sh", "start", "--optimized"]