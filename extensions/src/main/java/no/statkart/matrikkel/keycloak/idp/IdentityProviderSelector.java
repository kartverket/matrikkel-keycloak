package no.statkart.matrikkel.keycloak.idp;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.freemarker.model.IdentityProviderBean;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.Constants;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.Urls;
import org.keycloak.services.resources.LoginActionsService;
import org.keycloak.theme.Theme;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.forms.login.freemarker.model.IdentityProviderBean.IDP_COMPARATOR_INSTANCE;

public class IdentityProviderSelector implements Authenticator {

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Map<String, String> config = Optional
                .ofNullable(context.getAuthenticatorConfig())
                .map(AuthenticatorConfigModel::getConfig)
                .orElse(Collections.emptyMap());
        boolean includeDefaultIpds = Boolean.parseBoolean(config.getOrDefault(
                IdentityProviderSelectorFactory.INCLUDE_DEFAULT_IDP_PROPERTY,
                Boolean.TRUE.toString()));
        List<String> additionalIdps = Stream
                .of(config.get(IdentityProviderSelectorFactory.INCLUDE_IDPS_PROPERTY))
                .filter(Objects::nonNull)
                .flatMap(s -> Arrays.stream(s.split("##")))
                .filter(s -> !s.trim().isEmpty())
                .collect(Collectors.toList());

        List<IdentityProviderModel> identityProviders = context
                .getRealm()
                .getIdentityProvidersStream()
                .filter(IdentityProviderModel::isEnabled)
                .filter(idp -> includeDefaultIpds && !idp.isHideOnLogin() || additionalIdps.contains(idp.getAlias()))
                .collect(Collectors.toList());

        String accessCode = context.generateAccessCode();
        UriBuilder uriBuilder = UriBuilder
                .fromUri(context.getUriInfo().getBaseUri().getPath())
                .queryParam(Constants.CLIENT_ID, context.getAuthenticationSession().getClient().getClientId())
                .queryParam(Constants.TAB_ID, context.getAuthenticationSession().getTabId())
                .queryParam(LoginActionsService.SESSION_CODE, accessCode);

        Response challenge = context
                .form()
                .setAttribute("socialselect", new TemplateBean(
                        context.getRealm(),
                        context.getSession(),
                        identityProviders,
                        uriBuilder.build()))
                .createForm("idp-select.ftl");
        context.forceChallenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        // trenger ikke å gjøre noe;
    }

    /** Omksriving av IdentityProviderBean for å kunne vise skjulte identity providers */
    public static class TemplateBean {
        private List<IdentityProviderBean.IdentityProvider> providers;
        private final KeycloakSession session;

        public TemplateBean(RealmModel realm, KeycloakSession session, List<IdentityProviderModel> identityProviders, URI baseURI) {
            this.session = session;

            if (!identityProviders.isEmpty()) {
                List<IdentityProviderBean.IdentityProvider> orderedList = new ArrayList<>();
                for (IdentityProviderModel identityProvider : identityProviders) {
                    if (identityProvider.isEnabled() && !identityProvider.isLinkOnly()) {
                        addIdentityProvider(orderedList, realm, baseURI, identityProvider);
                    }
                }

                if (!orderedList.isEmpty()) {
                    orderedList.sort(IDP_COMPARATOR_INSTANCE);
                    providers = orderedList;
                }
            }
        }

        private void addIdentityProvider(List<IdentityProviderBean.IdentityProvider> orderedSet, RealmModel realm, URI baseURI, IdentityProviderModel identityProvider) {
            String loginUrl = Urls.identityProviderAuthnRequest(baseURI, identityProvider.getAlias(), realm.getName()).toString();
            String displayName = KeycloakModelUtils.getIdentityProviderDisplayName(session, identityProvider);
            Map<String, String> config = identityProvider.getConfig();

                orderedSet.add(new IdentityProviderBean.IdentityProvider(identityProvider.getAlias(),
                        displayName, identityProvider.getProviderId(), loginUrl,
                        config != null ? config.get("guiOrder") : null, getLoginIconClasses(identityProvider.getAlias())));
        }

        private String getLoginIconClasses(String alias) {
            final String ICON_THEME_PREFIX = "kcLogoIdP-";

            try {
                Theme theme = session.theme().getTheme(Theme.Type.LOGIN);
                return Optional.ofNullable(theme.getProperties().getProperty(ICON_THEME_PREFIX + alias)).orElse("");
            } catch (IOException e) {
                //NOP
            }
            return "";
        }

        public List<IdentityProviderBean.IdentityProvider> getProviders() {
            return providers;
        }

        public boolean isDisplayInfo() {
            return  true;
        }
    }
}
