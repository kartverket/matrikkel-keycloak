package no.statkart.matrikkel.keycloak.scheduler;

import org.keycloak.provider.ProviderFactory;

public interface ScheduledTaskFactory<T extends ScheduledTask> extends ProviderFactory<T> {
}
