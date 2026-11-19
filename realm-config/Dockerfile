# Config-image for matrikkel-keycloak: keycloak-config-cli + realm-konfigurasjon per miljø (envs/<miljø>/).
# Bygges av .github/workflows/realm-config.yml med context ./realm-config.
#
#   docker build -t matrikkel-keycloak-config realm-config/

ARG JDK_BUILD_IMAGE=eclipse-temurin:21-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a
ARG JRE_RUNTIME_IMAGE=eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
ARG CONFIG_CLI_VERSION=6.5.1
ARG CONFIG_CLI_SHA256=d30f9832f9880a525a17802b74d3bfc826e4fd86eceaa39e7a37f71302b586f4
ARG KEYCLOAK_VERSION=26.8.2
ARG KEYCLOAK_CLIENT_VERSION=26.0.8

# keycloak-config-cli bygges fra kildekode mot vår Keycloak-versjon. Ferdigbygde images fra adorsys
# ligger etter Keycloak (nyeste er 26.5.5), og importen avviser felt som er nye i Keycloak, for eksempel
# maxSecondaryAuthFailures i 26.8. KEYCLOAK_VERSION skal følge Keycloak-imaget (extensions/build.gradle).
FROM ${JDK_BUILD_IMAGE} AS config-cli
ARG CONFIG_CLI_VERSION
ARG CONFIG_CLI_SHA256
ARG KEYCLOAK_VERSION
ARG KEYCLOAK_CLIENT_VERSION
WORKDIR /build
ADD --checksum=sha256:${CONFIG_CLI_SHA256} \
    https://github.com/adorsys/keycloak-config-cli/archive/refs/tags/v${CONFIG_CLI_VERSION}.tar.gz /build/src.tar.gz
RUN tar xzf src.tar.gz --strip-components=1 \
 && ./mvnw -ntp -B -q clean package -DskipTests \
      -Dkeycloak.version=${KEYCLOAK_VERSION} -Dkeycloak.client.version=${KEYCLOAK_CLIENT_VERSION} \
      -Dlicense.skipCheckLicense -Dcheckstyle.skip -Dmaven.test.skip=true -Dmaven.site.skip=true \
      -Dmaven.javadoc.skip=true -Dmaven.gitcommitid.skip=true

FROM ${JRE_RUNTIME_IMAGE} AS apply
LABEL org.opencontainers.image.source=https://github.com/kartverket/matrikkel-keycloak
COPY --from=config-cli /build/target/keycloak-config-cli.jar /app/keycloak-config-cli.jar
COPY envs /config
# Bootstrap av apply-klienten i master (kjøres manuelt med admin, se README)
COPY master /master
USER 65534
ENTRYPOINT ["java", "-jar", "/app/keycloak-config-cli.jar"]

# CONFIG_ENV (dev), KEYCLOAK_URL og KEYCLOAK_CLIENTSECRET settes i SKIPJob.
# ${CONFIG_ENV} løses av Spring ved oppstart; jobben feiler hvis den mangler.
ENV IMPORT_FILES_LOCATIONS="file:/config/\${CONFIG_ENV}/*.json" \
    KEYCLOAK_LOGINREALM=master \
    KEYCLOAK_CLIENTID=matrikkel-keycloak-config \
    KEYCLOAK_GRANTTYPE=client_credentials \
    KEYCLOAK_AVAILABILITYCHECK_ENABLED=true \
    KEYCLOAK_AVAILABILITYCHECK_TIMEOUT=300s \
    IMPORT_VALIDATE=true \
    IMPORT_REMOTESTATE_ENABLED=true \
    IMPORT_CACHE_ENABLED=false \
    IMPORT_VARSUBSTITUTION_ENABLED=true \
    IMPORT_VARSUBSTITUTION_UNDEFINEDISERROR=true \
    TZ=Europe/Oslo

# config-cli bruker 'full' som standard, altså sletter den det som mangler i configen. Vi starter med
# no-delete for alt, slik at ingenting slettes. Se "Livsløp" i realm-config/README.md.
ENV IMPORT_MANAGED_AUTHENTICATIONFLOW=no-delete \
    IMPORT_MANAGED_GROUP=no-delete \
    IMPORT_MANAGED_REQUIREDACTION=no-delete \
    IMPORT_MANAGED_CLIENTSCOPE=no-delete \
    IMPORT_MANAGED_SCOPEMAPPING=no-delete \
    IMPORT_MANAGED_CLIENTSCOPEMAPPING=no-delete \
    IMPORT_MANAGED_COMPONENT=no-delete \
    IMPORT_MANAGED_SUBCOMPONENT=no-delete \
    IMPORT_MANAGED_IDENTITYPROVIDER=no-delete \
    IMPORT_MANAGED_IDENTITYPROVIDERMAPPER=no-delete \
    IMPORT_MANAGED_ROLE=no-delete \
    IMPORT_MANAGED_CLIENT=no-delete \
    IMPORT_MANAGED_CLIENTAUTHORIZATIONRESOURCES=no-delete \
    IMPORT_MANAGED_CLIENTAUTHORIZATIONPOLICIES=no-delete \
    IMPORT_MANAGED_CLIENTAUTHORIZATIONSCOPES=no-delete \
    IMPORT_MANAGED_MESSAGEBUNDLES=no-delete \
    IMPORT_MANAGED_WORKFLOW=no-delete
