package no.statkart.matrikkel.keycloak.email;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;

public class GraphServiceClientFactory {

    private GraphServiceClientFactory() {
    }

    static GraphServiceClient create(OauthConfig oauthConfig) {
        final ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(oauthConfig.getClientId())
                .clientSecret(oauthConfig.getSecretId())
                .tenantId(oauthConfig.getTenantId())
                .build();

        GraphServiceClient graphClient = new GraphServiceClient(clientSecretCredential, "https://graph.microsoft.com/.default");
        return graphClient;
    }

}
