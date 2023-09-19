package no.statkart.matrikkel.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.ExistingUserInfo;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Execution for visning i nettleser, som lar bruker skrive inn ett brukernavn
 * som blir brukt ved linking av identity-provider bruker til keycloak bruker
 */
public class IdpChooseUsername implements Authenticator, AuthenticatorFactory {
    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.challenge(context.form().createLoginUsername());
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        UserModel user = getUser(context);

        if (user == null) {
            Response challenge = context.form()
                    .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.INVALID_USER))
                    .createLoginUsername();
            context.failureChallenge(AuthenticationFlowError.INVALID_USER, challenge);
            return;
        }

        if (user.getEmail() == null || user.getEmail().isEmpty()) {
            // Bekreft med e-post authenticatoren failer ikke når bruker ikke har satt e-post, så vi
            // gjør det her
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SerializedBrokeredIdentityContext serializedCtx = SerializedBrokeredIdentityContext
                .readFromAuthenticationSession(authSession, "BROKERED_CONTEXT");

        if (context
                .getSession()
                .users()
                .getFederatedIdentitiesStream(context.getRealm(), user)
                .map(FederatedIdentityModel::getIdentityProvider)
                .anyMatch(serializedCtx.getIdentityProviderId()::equals)) {
            // brukernavn oppgitt er allerede tilboblet denne idp
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }

        ExistingUserInfo existingUserInfo = new ExistingUserInfo(
                user.getId(),
                UserModel.USERNAME,
                user.getUsername()
        );
        authSession.setAuthNote(
                AbstractIdpAuthenticator.EXISTING_USER_INFO,
                existingUserInfo.serialize());

        context.success();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // do nothing
    }

    @Override
    public String getDisplayType() {
        return "Velg bruker for first broker login";
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
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Lar bruker overstyre brukernavn fra identity provider";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return null;
    }

    @Override
    public IdpChooseUsername create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
        // do nothing
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // do nothing
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public String getId() {
        return "idp-choose-user-name";
    }

    private UserModel getUser(AuthenticationFlowContext context) {
        String username = getUsername(context);
        if (username == null) {
            return null;
        }
        return context.getSession().users().getUserByUsername(context.getRealm(), username);
    }

    private String getUsername(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context
                .getHttpRequest()
                .getDecodedFormParameters();
        String username = formData.getFirst("username");
        if (username != null) {
            username = username.trim();
            if (username.isEmpty()) {
                username = null;
            }
        }
        return username;
    }
}
