package no.statkart.matrikkel.keycloak.email;

public class OauthConfig {

    private final String tenantId;
    private final String clientId;
    private final String secretId;
    private final String userId;

    public OauthConfig(String tenantId, String clientId, String secretId, String userId) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.secretId = secretId;
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSecretId() {
        return secretId;
    }

    public String getUserId() {
        return userId;
    }
}
