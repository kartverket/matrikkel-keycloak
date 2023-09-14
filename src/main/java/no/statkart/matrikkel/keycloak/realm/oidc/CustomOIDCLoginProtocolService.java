package no.statkart.matrikkel.keycloak.realm.oidc;

import com.google.common.io.Resources;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.oidc.OIDCLoginProtocolService;
import org.keycloak.protocol.oidc.OIDCProviderConfig;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;


/**
 * Custom versjon av OIDCLoginProtocolService som utvider den innebygde protokollen "openid-connect" med et ekstra endepunktet
 * som ble fjernet fra en tidligere versjon av keycloak.
 *
 * De eksterne klientene redirecter til dette endepunktet ved vellykket innlogging. Må derfor beholde dette endepunktet frem til
 * alle klienter har omskrevet sin innloggingsflyt
 */
public class CustomOIDCLoginProtocolService extends OIDCLoginProtocolService {
    public CustomOIDCLoginProtocolService(KeycloakSession session, EventBuilder event, OIDCProviderConfig providerConfig) {
        super(session, event, providerConfig);
    }

    @GET
    @Path("delegated")
    @Produces({MediaType.TEXT_HTML})
    public Response delegated(@QueryParam("error") boolean error) throws IOException {
        URL url = Resources.getResource("/LoginSuccess.html");
        String loginSuccessPage = Resources.toString(url, StandardCharsets.UTF_8);

        return Response.ok(loginSuccessPage, MediaType.TEXT_HTML).build();
    }

}
