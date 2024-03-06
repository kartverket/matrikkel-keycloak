package no.statkart.matrikkel.keycloak.resource;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import no.statkart.matrikkel.keycloak.scheduler.ScheduledTask;
import org.keycloak.models.KeycloakSession;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;

import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class ScheduledTasksResource {
    private KeycloakSession session;
    private AuthenticationManager.AuthResult auth;

    @Inject
    public void setSession(KeycloakSession session) {
        this.session = session;
        auth = new AppAuthManager.BearerTokenAuthenticator(session)
                .setAudience(MatrikkelResource.CLIENT_ID)
                .authenticate();
    }

    @GET
    public List<ScheduledTaskDTO> getScheduledTaskDTOs() {
        verifyScheduledTaskAdmin();
        return session
                .getKeycloakSessionFactory()
                .getProviderFactoriesStream(ScheduledTask.class)
                .map(ProviderFactory::getId)
                .map(id -> getScheduledTaskForIdStream(id).orElseGet(() -> new ScheduledTaskDTO(id, "# never")))
                .collect(toList());
    }

    @POST
    @Path("{id}/execute")
    public void executeScheduledTask(@PathParam("id") String id) {
        verifyScheduledTaskAdmin();
        ScheduledTask task = session.getProvider(ScheduledTask.class, id);
        if (task == null) {
            throw new NotFoundException();
        } else {
            task.accept(session);
        }
    }

    private void verifyScheduledTaskAdmin() {
        AccessToken.Access access = getAccess();
        if (!access.isUserInRole(MatrikkelResource.SCHEDULED_TASK_ADMIN_ROLE)) {
            throw new NotAuthorizedException("Bearer");
        }
    }

    private AccessToken.Access getAccess() {
        if (auth == null || auth.getToken() == null) {
            throw new NotAuthorizedException("Bearer");
        }
        AccessToken.Access access = auth.getToken().getResourceAccess(MatrikkelResource.CLIENT_ID);
        if (access == null) {
            throw new NotAuthorizedException("Bearer");
        }
        return access;
    }

    private Optional<ScheduledTaskDTO> getScheduledTaskForIdStream(String id) {
        return Optional
                .ofNullable(session.getProvider(ScheduledTask.class, id))
                .flatMap(scheduledTask -> scheduledTask.getCron(session.getContext().getRealm()))
                .map(cron -> new ScheduledTaskDTO(id, cron.asString()));
    }
}
