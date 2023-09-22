package no.statkart.matrikkel.keycloak.idp;

import org.keycloak.OAuth2Constants;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.ClientSessionCode;
import org.keycloak.services.resources.LoginActionsService;
import org.keycloak.sessions.AuthenticationSessionModel;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

/**
 * Dette er den vanlige OpenID Connect Identity Provideren, utvidet med
 * at den returnerer til påloggingsflyten hvis den er automatisk vidresendt av
 * {@link ErrorHandlingIdentityProviderRedirector} til autorisasjonstjener og
 * denne returnerer feil
 */
public class FlowReturningOIDCIdentityProvider extends OIDCIdentityProvider {
    public FlowReturningOIDCIdentityProvider(KeycloakSession session, OIDCIdentityProviderConfig config) {
        super(session, config);
    }

    @Override
    public Object callback(RealmModel realm, AuthenticationCallback callback, EventBuilder event) {
        return new FlowReturningOIDCEndpoint(callback, realm, event);
    }

    protected class FlowReturningOIDCEndpoint extends OIDCEndpoint {
        public FlowReturningOIDCEndpoint(AuthenticationCallback callback, RealmModel realm, EventBuilder event) {
            super(callback, realm, event, FlowReturningOIDCIdentityProvider.this);
        }

        @Override
        public Response authResponse(String state, String authorizationCode, String error) {
            if (state == null || error == null) {
                // ingen session, eller ingen error betyr default håndtering
                return super.authResponse(state, authorizationCode, error);
            }

            // Hvis vi ikke er redirected til OIDC-provider av en ErrorHandlingIdentityProviderRedirectorFactory,
            // så blir det standard håndtering av responsen fra OIDC-provideren
            AuthenticationSessionModel authSession = callback.getAndVerifyAuthenticationSession(state);
            String executionId = authSession.getAuthNote(AuthenticationProcessor.CURRENT_AUTHENTICATION_EXECUTION);
            if (executionId == null) {
                return super.authResponse(state, authorizationCode, error);
            }
            AuthenticationExecutionModel authExecution = authSession.getRealm().getAuthenticationExecutionById(executionId);
            if (!authExecution.getAuthenticator().equals(ErrorHandlingIdentityProviderRedirectorFactory.MATRIKKEL_IDP_REDIRECTOR)) {
                return super.authResponse(state, authorizationCode, error);
            }

            // Redirect tilbake til flyten (som nå er på en ErrorHandlingIdentityProviderRedirectorFactory)
            UriBuilder uriBuilder = LoginActionsService.loginActionsBaseUrl(session.getContext().getUri())
                    .path(LoginActionsService.AUTHENTICATE_PATH)
                    .queryParam(LoginActionsService.AUTH_SESSION_ID, authSession.getParentSession().getId())
                    .queryParam(LoginActionsService.SESSION_CODE, new ClientSessionCode<>(session, realm, authSession).getOrGenerateCode())
                    .queryParam(Constants.EXECUTION, executionId)
                    .queryParam(Constants.CLIENT_ID, authSession.getClient().getClientId())
                    .queryParam(Constants.TAB_ID, authSession.getTabId())
                    .queryParam(ErrorHandlingIdentityProviderRedirector.ERROR, error);
            String errorDescription = httpRequest.getUri().getQueryParameters().getFirst(OAuth2Constants.ERROR_DESCRIPTION);
            if (errorDescription != null && !errorDescription.trim().isEmpty()) {
                uriBuilder.queryParam(ErrorHandlingIdentityProviderRedirector.ERROR_DESCRIPTION, errorDescription);
            }
            return Response.seeOther(uriBuilder.build(realm.getName())).build();
        }
    }
}
