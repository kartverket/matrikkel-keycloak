package no.statkart.matrikkel.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.common.ClientConnection;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ClientNetworkConditionalAuthenticator implements ConditionalAuthenticator, ConditionalAuthenticatorFactory {
    private static final Logger log = Logger.getLogger(ClientNetworkConditionalAuthenticator.class);
    private static final String INVALID_URL = "-INVALID-URL-";
    public static final String MATCH_STRINGS_PROPERTY = "match";

    private volatile String cachedMatchPatternString = "";
    private Pattern cachedMatchPattern;

    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        ensureCachedMatchPatternUpToDate(context);
        ClientConnection clientConnection = context.getConnection();
        return cachedMatchPattern.matcher(clientConnection.getRemoteAddr()).matches()
                || cachedMatchPattern.matcher(clientConnection.getRemoteHost()).matches();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        // do nothing
    }

    @Override
    public boolean requiresUser() {
        return true;
    }
    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // do nothing
    }
    @Override
    public ConditionalAuthenticator getSingleton() {
        return this;
    }
    @Override
    public String getDisplayType() {
        return "Condition - Klientnettverk";
    }

    @Override
    public String getReferenceCategory() {
        return "condition";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.DISABLED};
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Klientnettverk condition";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.singletonList(new ProviderConfigProperty(
                MATCH_STRINGS_PROPERTY,
                "Nettverksnavn",
                "Nettverksnavn regex",
                ProviderConfigProperty.MULTIVALUED_STRING_TYPE
                , null)
        );
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
        return "mat-cond-client-network";
    }

    private void ensureCachedMatchPatternUpToDate(AuthenticationFlowContext context) {
        AuthenticatorConfigModel configModel = context.getAuthenticatorConfig();
        Map<String, String> config = configModel != null ? configModel.getConfig() : Collections.emptyMap();
        String match = config.getOrDefault(MATCH_STRINGS_PROPERTY, INVALID_URL);
        if (!cachedMatchPatternString.equals(match)) {
            synchronized (this) {
                if (!cachedMatchPatternString.equals(match)) {
                    try {
                        cachedMatchPattern = Pattern.compile(match.replace("##", "|"), Pattern.CASE_INSENSITIVE);
                    } catch (PatternSyntaxException e) {
                        log.errorf(e, "%s er er feilkonfigurert", getId());
                    }
                    cachedMatchPatternString = match;
                }
            }
        }
    }

}
