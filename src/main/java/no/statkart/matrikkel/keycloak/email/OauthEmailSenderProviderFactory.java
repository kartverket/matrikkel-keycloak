package no.statkart.matrikkel.keycloak.email;

import org.keycloak.email.EmailSenderProvider;
import org.keycloak.email.EmailSenderProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.util.Optional;

public class OauthEmailSenderProviderFactory implements EmailSenderProviderFactory {

    @Override
    public EmailSenderProvider create(KeycloakSession session) {
        OauthConfig oauthConfig = new OauthConfig(
                readEnvVariable("KEYCLOAK_EMAIL_OAUTH_TENANT_ID"),
                readEnvVariable("KEYCLOAK_EMAIL_OAUTH_CLIENT_ID"),
                readEnvVariable("KEYCLOAK_EMAIL_OAUTH_SECRET_ID"),
                readEnvVariable("KEYCLOAK_EMAIL_OAUTH_USER_ID")
        );

        return new OauthEmailSenderProvider(
                GraphServiceClientFactory.create(oauthConfig),
                oauthConfig.getUserId(),
                session
        );
    }

    private static String readEnvVariable(String envVariable) {
        return Optional.ofNullable(System.getenv(envVariable))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Could not resolve env variable; %s", envVariable)));
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return "oauth-email-provider";
    }
}
