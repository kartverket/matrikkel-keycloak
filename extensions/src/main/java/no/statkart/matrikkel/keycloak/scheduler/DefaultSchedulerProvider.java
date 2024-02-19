package no.statkart.matrikkel.keycloak.scheduler;

import com.cronutils.model.time.ExecutionTime;
import org.jboss.logging.Logger;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.Config;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.common.util.Resteasy;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.PostMigrationEvent;
import org.keycloak.services.scheduled.ClusterAwareScheduledTaskRunner;
import org.keycloak.timer.TimerProvider;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.UrlType;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DefaultSchedulerProvider implements SchedulerProviderFactory<DefaultSchedulerProvider>, SchedulerProvider {
    private static final long ONE_MINUTE_MILLIS = Duration.ofMinutes(1L).toMillis();
    private static final String CLUSTER_STARTUP_TIME_KEY = DefaultSchedulerProvider.class.getName() + ".clusterStartupTime";
    private static final String SCHEDULER_STARTUP_TIME_KEY = DefaultSchedulerProvider.class.getName() + ".schedulerStartupTime";
    private static final String INVALID_HOST = "example.com";

    private static final Logger log = Logger.getLogger(DefaultSchedulerProvider.class);
    private static final ResteasyUriInfo invalidUriInfo = new ResteasyUriInfo("http://" + INVALID_HOST, "/invalid");

    private Instant schedulerStartupTime;

    @Override
    public DefaultSchedulerProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(event -> {
            if (event instanceof PostMigrationEvent) {
                // Man får random kræsjer hvis man prøver å bruke JPA før
                // PostMigrationEvent
                lateInit(factory);
            }
        });
    }

    private synchronized void lateInit(KeycloakSessionFactory factory) {
        if (schedulerStartupTime != null) {
            throw new IllegalStateException();
        }

        // Idéen her er å bli enig om et tidspunkt der clusteret har startet, og så "sier" vi
        // at alle scheduled tasks siste kjøretid er akkurat når clusteret har startet. Når koden
        // i TaskWrapper da sjekker om det er noen oppgaver som skulle vært kjørt mellom siste
        // kjøretidspunkt og "nå", så vil den da bare kjøre de som har vært "misset" mens clusteret
        // faktisk er oppe.
        Instant[] schedulerStartupTime = new Instant[1];
        while (schedulerStartupTime[0] == null) {
            KeycloakModelUtils.runJobInTransaction(factory, session -> {
                Instant now = Instant.now();
                RealmModel adminRealm = session.realms().getRealmByName(Config.getAdminRealm());
                Map<String, ScheduledTask> scheduledTasks = session.listProviderIds(ScheduledTask.class).stream().collect(Collectors.toMap(Function.identity(), id -> session.getProvider(ScheduledTask.class, id)));
                ClusterProvider provider = session.getProvider(ClusterProvider.class);
                provider.executeIfNotExecuted(getClass().getSimpleName(), 900, () -> {
                    int clusterStartupTime = provider.getClusterStartupTime();
                    if (clusterStartupTime != adminRealm.getAttribute(CLUSTER_STARTUP_TIME_KEY, Integer.MIN_VALUE)) {
                        log.tracef("Setting scheduler clustered startup time to %s",
                                now.atZone(ZoneId.systemDefault()).toLocalTime());
                        adminRealm.setAttribute(CLUSTER_STARTUP_TIME_KEY, clusterStartupTime);
                        adminRealm.setAttribute(SCHEDULER_STARTUP_TIME_KEY, now.getEpochSecond());
                        session.realms().getRealmsStream().forEach(realm -> scheduledTasks.forEach((s, scheduledTask) -> realm.setAttribute(getScedulerLastExecutionKey(s, realm), now.getEpochSecond())));
                    }

                    if (clusterStartupTime == adminRealm.getAttribute(CLUSTER_STARTUP_TIME_KEY, Integer.MIN_VALUE)) {
                        schedulerStartupTime[0] = Instant.ofEpochSecond(Long.parseLong(adminRealm.getAttribute(SCHEDULER_STARTUP_TIME_KEY)));
                        log.infof("Schedulered cluster startup time is %s", schedulerStartupTime[0].atZone(ZoneId.systemDefault()).toLocalTime());
                    }
                    return null;
                });
            });
        }
        this.schedulerStartupTime = schedulerStartupTime[0];

        KeycloakSession session = factory.create();
        try {
            TimerProvider timer = session.getProvider(TimerProvider.class);
            Map<String, ScheduledTask> scheduledTasks = session.listProviderIds(ScheduledTask.class).stream().collect(Collectors.toMap(Function.identity(), id -> session.getProvider(ScheduledTask.class, id)));
            for (Map.Entry<String, ScheduledTask> entry : scheduledTasks.entrySet()) {
                String id = entry.getKey();
                ScheduledTask task = entry.getValue();
                log.infof("Creating cluster aware timer for %s", id);
                ClusterAwareScheduledTaskRunner clusterAwareScheduledTaskRunner = new ClusterAwareScheduledTaskRunner(
                        factory,
                        new TaskWrapper(id, task),
                        ONE_MINUTE_MILLIS);
                timer.schedule(clusterAwareScheduledTaskRunner, ONE_MINUTE_MILLIS, id);
            }
        } finally {
            session.close();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return "default";
    }

    private static String getScedulerLastExecutionKey(String id, RealmModel realm) {
        return id + "." + realm.getName() + ".lastExecutionTime";
    }

    private class TaskWrapper implements org.keycloak.timer.ScheduledTask {

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
            String taskLastExecutionKey = getScedulerLastExecutionKey(id, realm);
            ZonedDateTime lastExecutionTime = Optional
                    .ofNullable(realm.getAttribute(taskLastExecutionKey))
                    .map(Long::valueOf)
                    .map(Instant::ofEpochSecond)
                    .map(instant -> instant.isBefore(schedulerStartupTime) ? schedulerStartupTime : instant)
                    .map(instant -> instant.atZone(now.getZone()))
                    .orElseGet(() -> schedulerStartupTime.atZone(now.getZone()));

            Optional<ZonedDateTime> nextExecutionOption = task
                    .getCron(realm)
                    .flatMap(cron -> ExecutionTime
                            .forCron(cron)
                            .nextExecution(lastExecutionTime
                                    .truncatedTo(ChronoUnit.MINUTES)
                                    .equals(lastExecutionTime) ? lastExecutionTime.minusNanos(1L)
                                                               : lastExecutionTime));
            if (!nextExecutionOption.isPresent()) {
                log.tracef("Skipping %s task for %s realm: No next execution time", id, realm.getName());
                return;
            }
            ZonedDateTime nextExecution = nextExecutionOption.get();

            if (nextExecution.compareTo(now) > 0) {
                log.tracef("Skipping %s task for %s realm: Next execution time is in the future (next is: %s)",
                        id, realm.getName(), nextExecution);
                return;
            }

            log.debugf("Executing %s task for %s realm", id, realm);
            try {
                Resteasy.pushContext(KeycloakSession.class,session);
                HostnameProvider hostnameProvider = session.getProvider(HostnameProvider.class);
                String scheme;
                try {
                    scheme = hostnameProvider.getScheme(null, UrlType.FRONTEND);
                } catch (NullPointerException e) {
                    scheme = "https";
                }
                String hostname;
                try {
                    hostname = hostnameProvider.getHostname(null, UrlType.FRONTEND);
                } catch (NullPointerException e) {
                    throw new RuntimeException("Frontend url må være konfigurert", e);
                }
                int port;
                try {
                    port = hostnameProvider.getPort(null, UrlType.FRONTEND);
                } catch (Exception e) {
                    port = -1;
                }
                String contextPath;
                try {
                    contextPath = hostnameProvider.getContextPath(null, UrlType.FRONTEND);
                } catch (NullPointerException e) {
                    contextPath = "/";
                }
                Resteasy.pushContext(HttpRequest.class, MockHttpRequest.create("POST", String.format("%s://%s%s/%s", scheme, hostname, (port > 0 ? ":" + port : ""), contextPath)));

                // Vi setter siste utført kjøretidspunkt etter tasken er ferdig, slik at tasker bare blir kjørt
                // én gang hvis de tar lengere tid en cron intervallet
                Long lastExecTime[] = new Long[1];


                int txTimeout = 900;
                KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), txSession -> {
                    RealmModel taskRealm = txSession.realms().getRealm(realm.getId());
                    String lastExecTimeString = taskRealm.getAttribute(taskLastExecutionKey);
                    lastExecTime[0] = Long.valueOf(lastExecTimeString != null ? lastExecTimeString : "0" );

                    taskRealm.setAttribute(taskLastExecutionKey, Instant.now().plus(txTimeout, ChronoUnit.SECONDS).getEpochSecond());
                    log.debugf("%s.%s LastExecTime = %s", taskRealm.getName(),taskLastExecutionKey, Instant.from(Instant.ofEpochMilli(lastExecTime[0])));
                });


                try {
                    KeycloakModelUtils.runJobInTransactionWithTimeout(session.getKeycloakSessionFactory(), taskSession -> {
                        RealmModel taskRealm = taskSession.realms().getRealm(realm.getId());
                        taskSession.getContext().setRealm(taskRealm);
                        taskSession.getContext().setClient(taskRealm.getMasterAdminClient());
                        Resteasy.pushContext(KeycloakSession.class,taskSession);
                        task.accept(taskSession);
                    }, txTimeout);
                } catch (Exception e) {
                    try {
                        KeycloakModelUtils.runJobInTransaction(session.getKeycloakSessionFactory(), txSession -> {
                            RealmModel taskRealm = txSession.realms().getRealm(realm.getId());
                            taskRealm.setAttribute(taskLastExecutionKey, lastExecTime[0]);
                        });

                    } catch (Exception ex) {
                        e.addSuppressed(ex);
                    } finally {
                        throw e;
                    }
                }
            } catch (Exception t) {
                log.errorf(t, "Task %s for %s realm failed", id, realm);
            } finally {
                Resteasy.clearContextData();
            }
        }
    }
}
