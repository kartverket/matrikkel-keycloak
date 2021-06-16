package no.statkart.matrikkel.keycloak.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import org.jboss.logging.Logger;
import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.keycloak.Config;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.common.util.Resteasy;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakTransactionManager;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.timer.TimerProvider;
import org.keycloak.urls.HostnameProvider;
import org.keycloak.urls.UrlType;

import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultSchedulerProvider implements SchedulerProviderFactory<DefaultSchedulerProvider>, SchedulerProvider {
    private static final long GRANULARITY_MILLIS = 60_000L;
    private static final Logger log = Logger.getLogger(DefaultSchedulerProvider.class);
    @SuppressWarnings("rawtypes")
    private static final Comparator<ProviderFactory> PROVIDER_FACTORY_COMPARATOR = Comparator
            .<ProviderFactory, Integer>comparing(ProviderFactory::order)
            .reversed()
            .thenComparing(ProviderFactory::getId);
    private Instant lastExecutionInstant;
    private List<String> scheduledTaskIds;
    private volatile KeycloakSessionFactory keycloakSessionFactory;
    private KeycloakSession keycloakSession;

    @Override
    public DefaultSchedulerProvider create(KeycloakSession keycloakSession) {
        synchronized (this) {
            if (this.keycloakSession != null) {
                throw new IllegalStateException("Multiple scheduler providers not allowed");
            }
            this.keycloakSession = keycloakSession;
        }
        TimerProvider timer = keycloakSession.getProvider(TimerProvider.class);
        lastExecutionInstant = Instant.now();
        timer.schedule(() -> {
            List<RealmModel> realms = getAllRealms(this.keycloakSession);
            keycloakSession
                    .getProvider(ClusterProvider.class)
                    .executeIfNotExecutedAsync(
                            getId() + "-timeout",
                            Math.toIntExact(GRANULARITY_MILLIS / 1333L),
                            (Callable<Boolean>) () -> {
                                try {
                                    for (String scheduledTaskId : scheduledTaskIds) {
                                        for (RealmModel realm : realms) {
                                            runScheduledTask(scheduledTaskId, realm, keycloakSessionFactory, lastExecutionInstant);
                                        }
                                    }
                                    return true;
                                } finally {
                                    lastExecutionInstant = lastExecutionInstant.plus(GRANULARITY_MILLIS, ChronoUnit.MILLIS);
                                }
                            });
        }, GRANULARITY_MILLIS, getId());


        return this;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        synchronized (this) {
            if (keycloakSessionFactory != null) {
                throw new IllegalStateException();
            }
            this.keycloakSessionFactory = factory;
        }
        KeycloakSession keycloakSession = factory.create();

        this.scheduledTaskIds = factory
                .getProviderFactoriesStream(ScheduledTask.class)
                .sorted(PROVIDER_FACTORY_COMPARATOR)
                .map(ProviderFactory::getId)
                .collect(Collectors.collectingAndThen(
                        Collectors.toList(),
                        Collections::unmodifiableList));
        create(keycloakSession);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (keycloakSession != null) {
                try {
                    keycloakSession.getProvider(TimerProvider.class).cancelTask(getId());
                } catch (Exception e) {
                    log.errorf("Canceling scheduled tasks failed", e);
                } finally {
                    keycloakSession.close();
                    keycloakSession = null;
                }
            }
            if (keycloakSessionFactory != null) {
                keycloakSessionFactory.close();
            }
        }
    }

    @Override
    public String getId() {
        return "default";
    }

    private List<RealmModel> getAllRealms(KeycloakSession timerKeycloakSession) {
        List<RealmModel> realms;
        KeycloakSession keycloakSession = timerKeycloakSession.getKeycloakSessionFactory().create();
        KeycloakTransactionManager transactionManager = keycloakSession.getTransactionManager();
        transactionManager.begin();
        try (Stream<RealmModel> realmsStream = keycloakSession.realms().getRealmsStream()) {
            realms = realmsStream.collect(Collectors.toList());
        } catch (Throwable t) {
            transactionManager.setRollbackOnly();
            throw t;
        } finally {
            transactionManager.rollback();
        }
        return realms;
    }

    private static void runScheduledTask(String scheduledTaskId, RealmModel realm, KeycloakSessionFactory keycloakSessionFactory, Instant lastExecutionInstant) {
        KeycloakSession keycloakSession = keycloakSessionFactory.create();
        try {
            keycloakSession.getContext().setRealm(realm);
            keycloakSession.getContext().setClient(realm.getMasterAdminClient());
            HostnameProvider hostnameProvider = keycloakSession.getProvider(HostnameProvider.class);
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
            Resteasy.pushContext(UriInfo.class, new ResteasyUriInfo(baseUri, URI.create('/' + realm.getName())));
            ScheduledTask scheduledTask = keycloakSession.getProvider(ScheduledTask.class, scheduledTaskId);
            Cron cron;
            ZoneId tz;
            try {
                Optional<Cron> cronOption = scheduledTask.getCron(realm);
                if (!cronOption.isPresent()) {
                    log.debugf("Scheduled task %s disabled for realm %s, no cron returned", scheduledTaskId, realm.getName());
                    return;
                }
                cron = cronOption.get();
                tz = Objects.requireNonNull(scheduledTask.getTimeZone(), "timeZone");
            } catch (Throwable t) {
                log.errorf(t, "Unable to get schedule for scheduled task %", scheduledTaskId);
                return;
            }
            ZonedDateTime currentExecutionTime = lastExecutionInstant.plus(GRANULARITY_MILLIS, ChronoUnit.MILLIS).atZone(tz);
            ExecutionTime.forCron(cron).timeFromLastExecution(currentExecutionTime).ifPresent(duration -> {
                if (duration.toMillis() <= GRANULARITY_MILLIS) {
                    KeycloakTransactionManager transactionManager = keycloakSession.getTransactionManager();
                    transactionManager.begin();
                    try {
                        scheduledTask.run();
                    } catch (Throwable t) {
                        transactionManager.setRollbackOnly();
                        log.errorf(t, "Scheduled task %s failed", scheduledTaskId);
                    } finally {
                        transactionManager.commit();
                    }
                }
            });
        } finally {
            Resteasy.clearContextData();
            keycloakSession.close();
        }
    }
}
