package no.statkart.matrikkel.keycloak.resource;

import javax.ws.rs.Path;

public class MatrikkelResource {

    public static final String CLIENT_ID = "matrikkel-realm-management";
    public static final String SCHEDULED_TASK_ADMIN_ROLE = "scheduled-task-admin";

    @Path("scheduled-tasks")
    public Class<ScheduledTasksResource> getScheduledTaskResource() {
        return ScheduledTasksResource.class;
    }
}
