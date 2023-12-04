package no.statkart.matrikkel.keycloak.email;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;

import java.util.List;

public class GraphServiceClientFactory {

    private GraphServiceClientFactory() {
    }

    static GraphServiceClient<Request> create(OauthConfig oauthConfig) {
        final ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(oauthConfig.getClientId())
                .clientSecret(oauthConfig.getSecretId())
                .tenantId(oauthConfig.getTenantId())
                .build();

        final TokenCredentialAuthProvider tokenCredentialAuthProvider = new TokenCredentialAuthProvider(
                List.of("https://graph.microsoft.com/.default"), clientSecretCredential);
        return GraphServiceClient
                .builder()
                .authenticationProvider(tokenCredentialAuthProvider)
                .buildClient();

    }

}
