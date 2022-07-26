package no.statkart.matrikkel.keycloak.idp;

import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;

public class FlowReturningOIDCIdentityProviderFactory extends OIDCIdentityProviderFactory {

    @Override
    public String getId() {
        return "flow-return-oidc";
    }

    @Override
    public String getName() {
        return "OpenID Connect v1.0 (flow return on error)";
    }

    @Override
    public FlowReturningOIDCIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new FlowReturningOIDCIdentityProvider(session, new OIDCIdentityProviderConfig(model));
    }
}
