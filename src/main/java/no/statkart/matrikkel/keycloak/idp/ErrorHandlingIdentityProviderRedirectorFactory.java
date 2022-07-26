package no.statkart.matrikkel.keycloak.idp;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;

public class ErrorHandlingIdentityProviderRedirectorFactory implements AuthenticatorFactory {
    static final String IDENTITY_PROVIDER_ID_KEY = "identityProviderId";
    static final String MATRIKKEL_IDP_REDIRECTOR = "mat-idp-redirector";

    @Override
    public String getDisplayType() {
        return "Vidresend til identitetslevrandør (non-interactive)";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[] {
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Videresender til konfigurert identity provider";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.singletonList(
                new ProviderConfigProperty(IDENTITY_PROVIDER_ID_KEY, "Identity Provider Alias", "Hvilken identity provider som skal redirects til", ProviderConfigProperty.STRING_TYPE, "")
        );
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return MATRIKKEL_IDP_REDIRECTOR;
    }

    private static final class SingletonHolder {
        private static final ErrorHandlingIdentityProviderRedirector INSTANCE = new ErrorHandlingIdentityProviderRedirector();
    }
}
