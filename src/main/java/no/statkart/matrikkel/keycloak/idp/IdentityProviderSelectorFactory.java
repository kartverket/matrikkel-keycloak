package no.statkart.matrikkel.keycloak.idp;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Arrays;
import java.util.List;

public class IdentityProviderSelectorFactory implements AuthenticatorFactory {

    public static final String INCLUDE_DEFAULT_IDP_PROPERTY = "default_idps";
    public static final String INCLUDE_IDPS_PROPERTY = "idps";

    @Override
    public String getDisplayType() {
        return "Velg påloggingsmetode";
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
                AuthenticationExecutionModel.Requirement.DISABLED,
                AuthenticationExecutionModel.Requirement.REQUIRED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Lar brukeren velge mellom forskjellige \"identity providers\" for pålogging";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Arrays.asList(
                new ProviderConfigProperty(
                        INCLUDE_DEFAULT_IDP_PROPERTY,
                        "Inkluder standard identitetslevrandører",
                        "Lar brukeren velge mellom alle ikke-skjulte identitetslevrandører",
                        ProviderConfigProperty.BOOLEAN_TYPE, "true"),
                new ProviderConfigProperty(
                        INCLUDE_IDPS_PROPERTY,
                        "Inkluder identitetslevrandør(er)",
                        "Hvilke identitetslevrandører man kan velge mellom",
                        ProviderConfigProperty.MULTIVALUED_STRING_TYPE,
                        null)
        );
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public void init(Config.Scope config) {
        // ingenting å gjøre
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // ingenting å gjøre
    }

    @Override
    public void close() {
        // ingenting å gjøre
    }

    @Override
    public String getId() {
        return "mat-choose-idp";
    }

    private static class SingletonHolder {
        private static final IdentityProviderSelector INSTANCE = new IdentityProviderSelector();
    }
}
