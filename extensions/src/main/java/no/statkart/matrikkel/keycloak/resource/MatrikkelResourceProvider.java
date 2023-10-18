package no.statkart.matrikkel.keycloak.resource;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class MatrikkelResourceProvider implements RealmResourceProvider {

    @Override
    public Class<MatrikkelResource> getResource() {
        return MatrikkelResource.class;
    }

    @Override
    public void close() {
        // trenger ikke å gjøre noe
    }

    public static class Factory implements RealmResourceProviderFactory {

        @Override
        public MatrikkelResourceProvider create(KeycloakSession session) {
            return new MatrikkelResourceProvider();
        }

        @Override
        public void init(Config.Scope config) {
            // trenger ikke å gjøre noe
        }

        @Override
        public void postInit(KeycloakSessionFactory factory) {
            // trenger ikke å gjøre noe
        }

        @Override
        public void close() {
            // trenger ikke å gjøre noe
        }

        @Override
        public String getId() {
            return "matrikkel";
        }

    }


}
