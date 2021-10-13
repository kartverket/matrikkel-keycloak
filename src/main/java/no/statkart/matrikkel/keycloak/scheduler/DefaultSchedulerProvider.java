package no.statkart.matrikkel.keycloak.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import org.jboss.logging.Logger;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.keycloak.Config;
import org.keycloak.common.util.Resteasy;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.services.scheduled.ClusterAwareScheduledTaskRunner;
import org.keycloak.timer.TimerProvider;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.UrlType;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public class DefaultSchedulerProvider implements SchedulerProviderFactory<DefaultSchedulerProvider>, SchedulerProvider {
    private static final long GRANULARITY_MILLIS = 60_000L;
    private static final Logger log = Logger.getLogger(DefaultSchedulerProvider.class);

    @Override
    public DefaultSchedulerProvider create(KeycloakSession session) {
        TimerProvider timer = session.getProvider(TimerProvider.class);
        Map<String, ScheduledTask> scheduledTasks = session
                .listProviderIds(ScheduledTask.class)
                .stream()
                .collect(toMap(
                        Function.identity(),
                        id -> session.getProvider(ScheduledTask.class, id)));

        for (Map.Entry<String, ScheduledTask> entry : scheduledTasks.entrySet()) {
            String id = entry.getKey();
            ScheduledTask task = entry.getValue();
            log.infof("Creating cluster aware timer for %s", id);
            ClusterAwareScheduledTaskRunner clusterAwareScheduledTaskRunner = new ClusterAwareScheduledTaskRunner(
                    session.getKeycloakSessionFactory(),
                    new TaskWrapper(id, task),
                    GRANULARITY_MILLIS);
            timer.schedule(clusterAwareScheduledTaskRunner, GRANULARITY_MILLIS, id);
        }
        return this;
    }


    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        KeycloakSession keycloakSession = factory.create();
        try {
            create(keycloakSession);
        } finally {
            keycloakSession.close();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "default";
    }

    private static class TaskWrapper implements org.keycloak.timer.ScheduledTask {

        private final String id;
        private final ScheduledTask task;

        public TaskWrapper(String id, ScheduledTask task) {
            this.id = id;
            this.task = task;
        }

        @Override
        public void run(KeycloakSession session) {
            ZonedDateTime now = ZonedDateTime.now(task.getTimeZone());
            session.realms().getRealmsStream().forEach(realm -> run(session, realm, now));
        }

        private void run(KeycloakSession session, RealmModel realm, ZonedDateTime now) {
            Optional<Cron> cronOption = task.getCron(realm);
            if (!cronOption.isPresent()) {
                log.tracef("Skipping %s task for %s realm: No cron", id, realm.getName());
            }
            cronOption
                .flatMap(cron -> {
                    Optional<ZonedDateTime> lastExecution = ExecutionTime.forCron(cron).lastExecution(now);
                    log.tracef(
                            "Last scheduled execution time for %s task for %s realm is %s",
                            id, realm.getName(),
                            lastExecution.map(ZonedDateTime::toLocalDateTime).map(LocalDateTime::toString)
                                    .orElse("never")
                    );
                    return lastExecution;
                })
                .ifPresent(lastExecution -> {
                    Duration duration = Duration.between(lastExecution, now);
                    long n = duration.toMillis();
                    if (n >= 0 && n < GRANULARITY_MILLIS * 2L) {
                        log.debugf("Executing %s task for %s realm", id, realm);
                        try {
                            KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), taskSession -> {
                                taskSession.getContext().setRealm(realm);
                                taskSession.getContext().setClient(realm.getMasterAdminClient());
                                HostnameProvider hostnameProvider = taskSession.getProvider(HostnameProvider.class);
                                URI baseUri;
                                try {
                                    baseUri = new URI(
                                            hostnameProvider.getScheme(null, UrlType.FRONTEND),
                                            hostnameProvider.getHostname(null, UrlType.FRONTEND),
                                            hostnameProvider.getContextPath(null, UrlType.FRONTEND),
                                            null);
                                } catch (URISyntaxException e) {
                                    throw new IllegalStateException(e);
                                }
                                try {
                                    Resteasy.pushContext(UriInfo.class, new ResteasyUriInfo(baseUri, URI.create('/' + realm.getName())));
                                    task.accept(taskSession);
                                } finally {
                                    Resteasy.clearContextData();
                                }
                            });
                        } catch (Exception t) {
                            log.errorf(t, "Task %s for %s realm failed", id, realm);
                        }
                    } else {
                        log.tracef("Skipping %s task for %s realm: %s too %s",
                                id, realm.getName(), duration,
                                (duration.isNegative() ? "early" : "late"));
                    }
                });
        }
    }
}
