package no.statkart.matrikkel.keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.http.ContentType;
import org.jboss.resteasy.spi.HttpResponseCodes;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

@Testcontainers
public class CustomEndepunktTest {

    @Container
    private static KeycloakContainer KEYCLOAK = new KeycloakContainer()
            .withRealmImportFiles("/matrikkelen-realm.json", "/matrikkelen-users-0.json")
            .withProviderLibsFrom(List.of(new File("build/libs/matrikkel-keycloak-extension-all.jar")))
            .withEnv("KEYCLOAK_EMAIL_OAUTH_TENANT_ID", "")
            .withEnv("KEYCLOAK_EMAIL_OAUTH_CLIENT_ID", "")
            .withEnv("KEYCLOAK_EMAIL_OAUTH_SECRET_ID", "")
            .withEnv("KEYCLOAK_EMAIL_OAUTH_USER_ID", "")
            .withAdminUsername("admin")
            .withAdminPassword("admin")
            .withContextPath("/auth");

    private String authServerUrl = KEYCLOAK.getAuthServerUrl();

    @Test
    public void given_et_gyldig_client_access_token_send_reminder_task_endpoint_returnerer_204() {
        //Henter access_token med en client verifikasjon
        String accessToken = given().contentType(ContentType.URLENC).formParams(Map.of(
                        "client_id", IntegrationTestConstants.CLIENT_ID_SCHEDULER,
                        "client_secret", IntegrationTestConstants .CLIENT_SECRET_SCHEDULER,
                        "grant_type", "client_credentials"

                )).post(authServerUrl + "/realms/matrikkelen/protocol/openid-connect/token")
                .then().assertThat().statusCode(200)
                .extract().path("access_token");

        //Bruker accesstoken hentet til å aktivere send-reminder-task
        given()
                .auth().oauth2(accessToken)
                .post(authServerUrl + "/realms/matrikkelen/tasks/send-reminder")
                .then()
                .assertThat().statusCode(HttpResponseCodes.SC_NO_CONTENT);
    }

    @Test
    public void given_et_ugyldig_clientId_access_token_send_reminder_task_endpoint_returnerer_500() {
        assertTrue(KEYCLOAK.isRunning());

        //Henter access_token med en client verifikasjon
        String accessToken = given().contentType(ContentType.URLENC).formParams(Map.of(
                        "client_id", IntegrationTestConstants .CLIENT_ID_MATRIKKEL,
                        "client_secret", IntegrationTestConstants .CLIENT_SECRET_MATRIKKEL,
                        "grant_type", "client_credentials"
                )).post(authServerUrl + "/realms/matrikkelen/protocol/openid-connect/token")
                .then()
                .assertThat().statusCode(200)
                .extract().path("access_token");

        // Bruker accesstoken hentet til å aktivere send-reminder-task
        given()
                .auth().oauth2(accessToken)
                .post(authServerUrl + "/realms/matrikkelen/tasks/send-reminder")
                .then()
                .assertThat().statusCode(HttpResponseCodes.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void given_et_ugyldig_bruker_access_token_send_reminder_task_endpoint_returnerer_500() throws IOException {
        assertTrue(KEYCLOAK.isRunning());

        Keycloak keycloak = Keycloak.getInstance(authServerUrl, "matrikkelen", "test", "asdasd123", "admin-cli");
        AccessTokenResponse accessTokenResponse = keycloak.tokenManager().getAccessToken();

        URL url = new URL(KEYCLOAK.getAuthServerUrl() + "/realms/matrikkelen/tasks/send-reminder");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + accessTokenResponse.getToken());

        assertThat(conn.getResponseCode(), is(HttpResponseCodes.SC_INTERNAL_SERVER_ERROR));
    }

    @Test
    public void custom_delegated_endepunkt_returnerer_200_med_forventet_innhold() throws Exception {
        assertTrue(KEYCLOAK.isRunning());

        URL url = new URL(KEYCLOAK.getAuthServerUrl() + "/realms/matrikkelen/protocol/openid-connect/delegated");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertThat(conn.getResponseCode(), is(HttpResponseCodes.SC_OK));
        assertThat(conn.getContentType(), is(ContentType.HTML.getAcceptHeader()));
    }
}


