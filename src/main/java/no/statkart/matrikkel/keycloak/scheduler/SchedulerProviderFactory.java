package no.statkart.matrikkel.keycloak.scheduler;

import org.keycloak.provider.ProviderFactory;

public interface SchedulerProviderFactory<T extends SchedulerProvider> extends ProviderFactory<T> {
}
