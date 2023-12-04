package no.statkart.matrikkel.keycloak.realm.oidc;

import org.keycloak.Config;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.OIDCLoginProtocolFactory;
import org.keycloak.protocol.oidc.OIDCProviderConfig;


public class CustomOIDCLoginProtocolFactory extends OIDCLoginProtocolFactory {

    private OIDCProviderConfig providerConfig;

    @Override
    public Object createProtocolEndpoint(KeycloakSession session, EventBuilder event) {
        return new CustomOIDCLoginProtocolService(session, event, providerConfig);
    }

    @Override
    public void init(Config.Scope config) {
        this.providerConfig = new OIDCProviderConfig(config);
        super.init(config);
    }

    /**
     * Ved å returnere et høyere tall enn den innebygde OIDCLoginProtocolFactory, vil denne provideren bli benyttet
     * i stedet for den innebygde når vi benytter samme ID som den innebygde.
     * <a href="https://www.keycloak.org/docs/latest/server_development/#_override_builtin_providers">docs</a>
     */
    @Override
    public int order() {
        return 1;
    }
}
