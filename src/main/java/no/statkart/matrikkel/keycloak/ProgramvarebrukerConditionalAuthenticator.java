package no.statkart.matrikkel.keycloak;

import no.statkart.matrikkel.keycloak.util.UserModels;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticator;
import org.keycloak.authentication.authenticators.conditional.ConditionalAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ProgramvarebrukerConditionalAuthenticator implements ConditionalAuthenticator, ConditionalAuthenticatorFactory {
    @Override
    public boolean matchCondition(AuthenticationFlowContext context) {
        Map<String, String> config = context.getAuthenticatorConfig().getConfig();
        boolean negateOutput = Boolean.parseBoolean(config.get("not"));

        UserModel user = context.getUser();
        if (user == null) {
            throw new AuthenticationFlowException(AuthenticationFlowError.UNKNOWN_USER);
        }

        boolean pvUser = UserModels.isProgramvarebruker(user);

        //noinspection SimplifiableConditionalExpression
        return negateOutput ? !pvUser : pvUser;
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
        return "Condition - Programvarebruker";
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
        return "Matrikkelprogramvarebruker condition";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty negateOutput = new ProviderConfigProperty();
        negateOutput.setType(ProviderConfigProperty.BOOLEAN_TYPE);
        negateOutput.setName("not");
        negateOutput.setLabel("Snu oppførsel");
        negateOutput.setHelpText("Condition gjelder for IKKE programvarebrukere");
        return Collections.singletonList(negateOutput);
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
        return "mat-cond-programvarebruker";
    }
}
