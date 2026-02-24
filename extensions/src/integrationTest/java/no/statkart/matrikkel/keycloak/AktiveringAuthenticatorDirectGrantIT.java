package no.statkart.matrikkel.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.AuthenticationExecutionInfoRepresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
public class AktiveringAuthenticatorDirectGrantIT {
    private static final String AKTIVERING_AUTHENTICATOR_ID = "matrikkel-aktivering-validate";

    @Container
    private static final KeycloakContainer KEYCLOAK = new KeycloakContainer()
            .withRealmImportFiles("/matrikkelen-realm.json", "/matrikkelen-users-1.json")
            .withProviderLibsFrom(List.of(new File("build/libs/matrikkel-keycloak-extension-all.jar")))
            .withEnv("KEYCLOAK_EMAIL_OAUTH_TENANT_ID", "")
            .withEnv("KEYCLOAK_EMAIL_OAUTH_CLIENT_ID", "")
            .withEnv("KEYCLOAK_EMAIL_OAUTH_SECRET_ID", "")
            .withEnv("KEYCLOAK_EMAIL_OAUTH_USER_ID", "")
            .withAdminUsername("admin")
            .withAdminPassword("admin")
            .withContextPath("/auth");

    private final String authServerUrl = KEYCLOAK.getAuthServerUrl();

    @BeforeEach
    void ensureDirectGrantFlow() {
        ensureAktiveringExecutionInDirectGrantFlow();
    }

    @Test
    public void password_grant_for_gyldig_bruker_returnerer_200_og_token() {
        Response response = passwordGrant("test-valid", "asdasd123");

        assertThat(response.statusCode(), is(200));
        assertThat(response.path("access_token"), is(notNullValue()));
    }

    @Test
    public void password_grant_for_passord_reaktivering_returnerer_invalid_grant_uten_uncaught_server_error() {
        Response response = passwordGrant("test-passord-reactivation", "asdasd123");

        assertThat(response.statusCode(), is(400));
        assertThat(response.path("error"), is("invalid_grant"));
        assertThat(response.path("error_description"), is("Password renewal grace period expired"));
    }

    @Test
    public void password_grant_for_utlopt_midlertidig_bruker_returnerer_invalid_grant_uten_uncaught_server_error() {
        Response response = passwordGrant("test-temp-expired", "asdasd123");

        assertThat(response.statusCode(), is(400));
        assertThat(response.path("error"), is("invalid_grant"));
        assertThat(response.path("error_description"), is("Temporary user disabled"));
    }

    private Response passwordGrant(String username, String password) {
        return given()
                .contentType(ContentType.URLENC)
                .formParams(Map.of(
                        "grant_type", "password",
                        "client_id", "admin-cli",
                        "username", username,
                        "password", password
                ))
                .post(authServerUrl + "/realms/matrikkelen/protocol/openid-connect/token")
                .andReturn();
    }

    private void ensureAktiveringExecutionInDirectGrantFlow() {
        try (Keycloak admin = Keycloak.getInstance(KEYCLOAK.getAuthServerUrl(), "master", "admin", "admin", "admin-cli")) {
            var flows = admin.realm("matrikkelen").flows();
            AuthenticationExecutionInfoRepresentation execution = findAktiveringAuthenticator(flows.getExecutions("direct grant"))
                    .orElseGet(() -> {
                        flows.addExecution("direct grant", Map.of("provider", AKTIVERING_AUTHENTICATOR_ID));
                        return findAktiveringAuthenticator(flows.getExecutions("direct grant"))
                                .orElseThrow(() -> new AssertionError("Fant ikke execution for " + AKTIVERING_AUTHENTICATOR_ID));
                    });

            if (!"REQUIRED".equals(execution.getRequirement())) {
                execution.setRequirement("REQUIRED");
                flows.updateExecutions("direct grant", execution);
            }
        }
    }

    private Optional<AuthenticationExecutionInfoRepresentation> findAktiveringAuthenticator(List<AuthenticationExecutionInfoRepresentation> providers) {
        return providers
                .stream()
                .filter(it -> AktiveringAuthenticatorDirectGrantIT.AKTIVERING_AUTHENTICATOR_ID.equals(it.getProviderId()))
                .findFirst();
    }
}
