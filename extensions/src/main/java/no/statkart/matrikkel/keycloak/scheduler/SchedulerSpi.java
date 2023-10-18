package no.statkart.matrikkel.keycloak.scheduler;

import org.keycloak.provider.Spi;

public final class SchedulerSpi implements Spi {
    @Override
    public boolean isInternal() {
        return false;
    }

    @Override
    public String getName() {
        return "matrikkel-scheduler";
    }

    @Override
    public Class<SchedulerProvider> getProviderClass() {
        return SchedulerProvider.class;
    }

    @Override
    public Class<SchedulerProviderFactory> getProviderFactoryClass() {
        return SchedulerProviderFactory.class;
    }
}
