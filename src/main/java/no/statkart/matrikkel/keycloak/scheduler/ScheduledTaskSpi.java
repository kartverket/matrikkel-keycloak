package no.statkart.matrikkel.keycloak.scheduler;

import org.keycloak.provider.Spi;

public final class ScheduledTaskSpi implements Spi {
    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "matrikkel-scheduled-task";
    }

    @Override
    public Class<ScheduledTask> getProviderClass() {
        return ScheduledTask.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<ScheduledTaskFactory<?>> getProviderFactoryClass() {
        return (Class) ScheduledTaskFactory.class;
    }
}
