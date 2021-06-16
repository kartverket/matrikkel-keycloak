package no.statkart.matrikkel.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.credential.hash.PasswordHashProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class SSHAPasswordHashProviderFactory implements PasswordHashProviderFactory {
    private static final Logger logger = Logger.getLogger(SSHAPasswordHashProviderFactory.class);

    public static final String ID = "SSHA";

	@Override
	public PasswordHashProvider create(KeycloakSession session) {
		return new SSHAPasswordHashProvider(getId());
	}

	@Override
	public void init(Scope config) {
	    logger.info("SSHAPasswordHashProviderFactory: init");
    }

	@Override
	public void postInit(KeycloakSessionFactory factory) {
	}

	@Override
	public void close() {
	}

	@Override
	public String getId() {
		return ID;
	}
}
