package no.statkart.matrikkel.keycloak.resource;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import no.statkart.matrikkel.keycloak.AktiveringReminderTask;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;


public class TasksResourceProvider implements RealmResourceProvider {

    public static final String CLIENT_ID = "matrikkel-realm-management";
    public static final String SEND_REMINDER_EMAIL = "send-reminder-email";
    private final KeycloakSession session;

    public TasksResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @POST
    @Path("/send-reminder")
    public void executeSendReminder() {
        verifyScheduledTaskAdmin();
        new AktiveringReminderTask().run(session);
    }

    private void verifyScheduledTaskAdmin() {
        AccessToken.Access access = getAccess(session);
        if (!access.isUserInRole(TasksResourceProvider.SEND_REMINDER_EMAIL)) {
            throw new NotAuthorizedException("Bearer");
        }
    }

    private static AccessToken.Access getAccess(KeycloakSession session) {
        AuthenticationManager.AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session)
                .setAudience(TasksResourceProvider.CLIENT_ID)
                .authenticate();
        if (session == null || auth.getToken() == null) {
            throw new NotAuthorizedException("Bearer");
        }
        AccessToken.Access access = auth.getToken().getResourceAccess(TasksResourceProvider.CLIENT_ID);
        if (access == null) {
            throw new NotAuthorizedException("Bearer");
        }
        return access;
    }

    @Override
    public TasksResourceProvider getResource() {
        return this;
    }

    @Override
    public void close() {

    }

    public static class Factory implements RealmResourceProviderFactory {

        @Override
        public TasksResourceProvider create(KeycloakSession session) {
            return new TasksResourceProvider(session);
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
            return "tasks";
        }

    }
}
