package no.statkart.matrikkel.keycloak;

import no.statkart.matrikkel.keycloak.util.UserModels;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class MidlertidigBrukerAuthenticator implements Authenticator, AuthenticatorFactory {
    private static final Logger logger = Logger.getLogger(MidlertidigBrukerAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String flowPath = context.getFlowPath();
        UserModel user = context.getUser();

        if (Instant.now().compareTo(UserModels.getExpirationDate(user).orElse(Instant.MAX)) >= 0) {
            logger.infov(
                    "Innlogging ({0}) feilet for {1}: Midlertidig bruker gått ut på dato",
                    flowPath, user.getUsername());

            // Midlertidig bruker har gått ut på dato
            Response errorPage = context
                    .form()
                    .setError("temporaryUserExpired")
                    .createErrorPage(Response.Status.FORBIDDEN);
            context.failure(AuthenticationFlowError.USER_DISABLED, errorPage);
        } else {
            context.success();
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // trengs ikke
    }

    @Override
    public void close() {
        // trengs ikke
    }


    @Override
    public String getDisplayType() {
        return "Midlertidig bruker autorisering for Matrikkelen";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Nekter midlertidige brukere som har utgått å logge på";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
        // trengs ikke
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // trengs ikke
    }

    @Override
    public String getId() {
        return "matrikkel-midlertidig-bruker";
    }

}
