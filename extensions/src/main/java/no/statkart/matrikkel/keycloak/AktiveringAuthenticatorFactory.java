package no.statkart.matrikkel.keycloak;

import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AktiveringAuthenticatorFactory implements AuthenticatorFactory {
    private static final List<AuthenticationExecutionModel.Requirement> AKTIVERING_REQUIRMENT_CHOICES =
            Collections.unmodifiableList(Arrays.asList(
                    AuthenticationExecutionModel.Requirement.REQUIRED,
                    AuthenticationExecutionModel.Requirement.DISABLED
            ));

    @Override
    public String getDisplayType() {
        return "Brukeraktiveringsvalidering for Matrikkelen";
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
        return AKTIVERING_REQUIRMENT_CHOICES.toArray(new AuthenticationExecutionModel.Requirement[0]);
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Håndterer regler for matrikkel bruker aktivering";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return Collections.emptyList();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SingletonHolder.INSTANCE;
    }

    @Override
    public void init(org.keycloak.Config.Scope config) {
        // trengs ikke
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // trengs ikke
    }

    @Override
    public void close() {
        // trengs ikke
    }

    @Override
    public String getId() {
        return "matrikkel-aktivering-validate";
    }

    private static class SingletonHolder {
        private static final AktiveringAuthenticator INSTANCE = new AktiveringAuthenticator();
    }
}
