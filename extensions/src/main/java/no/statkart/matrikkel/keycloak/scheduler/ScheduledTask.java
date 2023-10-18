package no.statkart.matrikkel.keycloak.scheduler;

import com.cronutils.model.Cron;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.Provider;

import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Consumer;

public interface ScheduledTask extends Provider, Consumer<KeycloakSession> {
    Optional<Cron> getCron(RealmModel realm);
    default ZoneId getTimeZone() {
        return ZoneId.systemDefault();
    }

    abstract class Simple implements ScheduledTaskFactory<ScheduledTask> {

        protected abstract Optional<Cron> getCron(RealmModel realm);
        protected abstract void run(KeycloakSession keycloakSession);
        protected ZoneId getTimeZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public void close() {
            // do nothing
        }

        @Override
        public final ScheduledTask create(KeycloakSession session) {
            return new ScheduledTask() {
                @Override
                public Optional<Cron> getCron(RealmModel realm) {
                    return Simple.this.getCron(realm);
                }

                @Override
                public ZoneId getTimeZone() {
                    return Simple.this.getTimeZone();
                }


                @Override
                public void accept(KeycloakSession session) {
                    Simple.this.run(session);
                }

                @Override
                public void close() {

                }
            };
        }

        @Override
        public final void init(Config.Scope config) {

        }

        @Override
        public final void postInit(KeycloakSessionFactory factory) {

        }


    }
}
