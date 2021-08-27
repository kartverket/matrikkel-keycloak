package no.statkart.matrikkel.keycloak;

import com.google.common.collect.ImmutableList;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.keycloak.Config;
import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.broker.oidc.mappers.AbstractClaimMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityProviderMapper;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class OidcBrokerIdHashedMapper implements IdentityProviderMapper {
    public static final String PROVIDER_ID = "oidc-broker-id-hashed-mapper";
    
    private static final List<ProviderConfigProperty> PROVIDER_CONFIG_PROPERTIES;
    private static final String DEFAULT_CLAIM_NAME = "sub";
    private static final String CLAIM_CONFIG_KEY = "claim";
    private static final String PEPPER_CONFIG_KEY = "pepper";

    static {
        PROVIDER_CONFIG_PROPERTIES = ImmutableList
                .<ProviderConfigProperty>builder()
                .add(new ProviderConfigProperty(
                        CLAIM_CONFIG_KEY,
                        "Claim",
                        "Claim",
                        ProviderConfigProperty.STRING_TYPE,
                        null))
                .add(new ProviderConfigProperty(
                        PEPPER_CONFIG_KEY,
                        "Pepper",
                        "Pepper",
                        ProviderConfigProperty.STRING_TYPE, null))
                .build();
    }

    @Override
    public String[] getCompatibleProviders() {
        return new String[] {OIDCIdentityProviderFactory.PROVIDER_ID};
    }

    @Override
    public String getDisplayCategory() {
        return "Preprocessor";
    }

    @Override
    public String getDisplayType() {
        return "Hashed Broker ID importer";
    }

    @Override
    public void preprocessFederatedIdentity(KeycloakSession session, RealmModel realm, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        String claimName = getClaimName(mapperModel);
        String claim = Objects.toString(AbstractClaimMapper.getClaimValue(context, claimName));
        if (!claim.isEmpty()) {
            byte[] pepper = getPepper(mapperModel);
            if (pepper == null || pepper.length == 0) {
                pepper = context.getIdpConfig().getInternalId().getBytes(StandardCharsets.UTF_8);
            }

            byte[] pw = claim.getBytes(StandardCharsets.UTF_8);
            Argon2BytesGenerator bytesGenerator = new Argon2BytesGenerator();
            bytesGenerator.init(new Argon2Parameters
                    .Builder(Argon2Parameters.ARGON2_id)
                    .withMemoryAsKB(16384)
                    .withIterations(8)
                    .withParallelism(2)
                    .withSecret(pepper)
                    .build());
            byte[] userIdBytes = new byte[24];
            bytesGenerator.generateBytes(pw, userIdBytes);
            String userId = Base64.getUrlEncoder().withoutPadding().encodeToString(userIdBytes);
            context.setId(userId);
            context.setUsername(userId.toLowerCase(Locale.ROOT));
        }
    }

    private static byte[] getPepper(IdentityProviderMapperModel mapperModel) {
        String pepperString = mapperModel.getConfig().get(PEPPER_CONFIG_KEY);
        byte[] pepper;
        if (pepperString != null && !pepperString.isEmpty()) {
            pepper = pepperString.getBytes(StandardCharsets.UTF_8);
        } else {
            pepper = null;
        }
        return pepper;
    }

    private static String getClaimName(IdentityProviderMapperModel mapperModel) {
        String claimName = mapperModel.getConfig().get(CLAIM_CONFIG_KEY);
        if (claimName == null || claimName.trim().isEmpty()) {
            claimName = DEFAULT_CLAIM_NAME;
        }
        return claimName;
    }

    @Override
    public void importNewUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {

    }

    @Override
    public final void updateBrokeredUserLegacy(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        updateBrokeredUser(session, realm, user, mapperModel, context);
    }

    @Override
    public void updateBrokeredUser(KeycloakSession session, RealmModel realm, UserModel user, IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
    }

    @Override
    public String getHelpText() {
        return "Preprocessor som setter Brokered ID til ett hashet verdi";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return PROVIDER_CONFIG_PROPERTIES;
    }

    @Override
    public IdentityProviderMapper create(KeycloakSession session) {
        return null;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public void close() {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
