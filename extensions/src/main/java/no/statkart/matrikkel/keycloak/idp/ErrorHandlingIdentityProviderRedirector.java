package no.statkart.matrikkel.keycloak.idp;

import org.jboss.logging.Logger;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationProcessor;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.IdentityProviderAuthenticator;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.services.Urls;
import org.keycloak.services.managers.ClientSessionCode;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Collections;
import java.util.Map;


public class ErrorHandlingIdentityProviderRedirector implements Authenticator {
    public static final String ERROR = "idp_" + OAuth2Constants.ERROR;
    public static final String ERROR_DESCRIPTION = "idp_" + OAuth2Constants.ERROR_DESCRIPTION;
    private static final String ORIGINAL_PROMPT_PARAM = ErrorHandlingIdentityProviderRedirector.class.getName() + "#prompt";
    private static final Logger log = Logger.getLogger(IdentityProviderAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (context.getAuthenticatorConfig() == null) {
            log.warnf("%s is not configured", getClass().getName());
            context.attempted();
            return;
        }
        Map<String, String> configMap = context.getAuthenticatorConfig().getConfig();

        String providerId = configMap.get(ErrorHandlingIdentityProviderRedirectorFactory.IDENTITY_PROVIDER_ID_KEY);
        if (providerId == null || providerId.isEmpty()) {
            log.warnf("%s is not configured", ErrorHandlingIdentityProviderRedirectorFactory.IDENTITY_PROVIDER_ID_KEY);
            context.attempted();
            return;
        }

        IdentityProviderModel idpModel = context.getRealm().getIdentityProviderByAlias(providerId);
        if (idpModel == null || !idpModel.isEnabled()) {
            log.warnf("Identity provider does not exist, or is disabled: %s", providerId);
            context.attempted();
            return;
        }

        redirectIdentityProviderNonInteractive(context, idpModel);
    }

    private void redirectIdentityProviderNonInteractive(AuthenticationFlowContext context, IdentityProviderModel idp) {
        Map<String, String> idpConfig = idp.getConfig() != null ? idp.getConfig() : Collections.emptyMap();
        String promptConfig = idpConfig.get("prompt");

        if (promptConfig != null && !promptConfig.equals(OIDCLoginProtocol.PROMPT_VALUE_NONE)) {
            // Hvis identity provideren er satt opp med prompt annet enn unspecified, overstyrer det
            // denne auth-sessionens parametere uansett. Vi kan bare feile her, siden vi kun vil ha
            // en ikke-interaktiv-pålogging hos identity provideren
            log.warnf("Identity provider does not allow non interactive logons: %s", idp.getAlias());
            context.attempted();
            return;
        }

        String accessCode = new ClientSessionCode<>(context.getSession(), context.getRealm(), context.getAuthenticationSession()).getOrGenerateCode();
        String clientId = context.getAuthenticationSession().getClient().getClientId();
        String tabId = context.getAuthenticationSession().getTabId();
        URI location = Urls.identityProviderAuthnRequest(context.getUriInfo().getBaseUri(), idp.getAlias(), context.getRealm().getName(), accessCode, clientId, tabId);
        if (context.getAuthenticationSession().getClientNote(OAuth2Constants.DISPLAY) != null) {
            location = UriBuilder.fromUri(location).queryParam(OAuth2Constants.DISPLAY, context.getAuthenticationSession().getClientNote(OAuth2Constants.DISPLAY)).build();
        }
        Response response = Response.seeOther(location)
                .build();

        // Ta vare på denne sesjonens prompt parameter, og tving den til å være none før vi gjør redirecten
        String promptParam = context.getAuthenticationSession().getClientNote(OIDCLoginProtocol.PROMPT_PARAM);
        if (promptParam != null) {
            context.getAuthenticationSession().setAuthNote(ORIGINAL_PROMPT_PARAM, promptParam);
            if ("none".equals(promptParam)) {
                //... hvis denne sesjonens prompt=none, så er det en "forwarding" av dette parameteret
                //    informer "ting" som skjer senere om dette
                context.getAuthenticationSession().setAuthNote(AuthenticationProcessor.FORWARDED_PASSIVE_LOGIN, "true");
            }
        }
        context.getAuthenticationSession().setClientNote(OIDCLoginProtocol.PROMPT_PARAM, OIDCLoginProtocol.PROMPT_VALUE_NONE);

        log.tracef("Vidresender til identity provider %s", idp.getAlias());
        context.forceChallenge(response);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        String originalPromptParam = context.getAuthenticationSession().getAuthNote(ORIGINAL_PROMPT_PARAM);
        if (originalPromptParam != null) {
            if (!"none".equals(originalPromptParam)) {
                context.getAuthenticationSession().setAuthNote(AuthenticationProcessor.FORWARDED_PASSIVE_LOGIN, "false");
            }
            context.getAuthenticationSession().setClientNote(OIDCLoginProtocol.PROMPT_PARAM, originalPromptParam);
        }
        MultivaluedMap<String, String> queryParameters = context.getUriInfo().getQueryParameters();
        String error = queryParameters.getFirst(ERROR);
        if (error == null) {
            error = "unknown_error";
        }

        Logger.Level severity = getLogLevel(error);
        if (log.isEnabled(severity)) {
            String errorDescription = queryParameters.getFirst(ERROR_DESCRIPTION);
            Map<String, String> configMap = context.getAuthenticatorConfig().getConfig();
            String providerId = configMap.get(ErrorHandlingIdentityProviderRedirectorFactory.IDENTITY_PROVIDER_ID_KEY);
            log.logf(severity, "Innlogging fra identity provider %s feilet, %s: %s", providerId, error, errorDescription);
        }

        context.attempted();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        // ingenting å gjøre
    }

    private Logger.Level getLogLevel(String error) {
        Logger.Level severity;
        switch (error) {
            case OAuthErrorException.TEMPORARILY_UNAVAILABLE:
            case OAuthErrorException.INTERACTION_REQUIRED:
            case OAuthErrorException.LOGIN_REQUIRED:
            case "account_selection_required":
            case "consent_required":
                severity = Logger.Level.DEBUG;
                break;
            default:
                severity = Logger.Level.WARN;
        }
        return severity;
    }

}
