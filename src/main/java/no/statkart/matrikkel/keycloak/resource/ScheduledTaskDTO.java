package no.statkart.matrikkel.keycloak.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ScheduledTaskDTO {
    protected static final String ID_PROPERTY_NAME = "id";
    protected static final String CRON_PROPERTY_NAME = "cron";
    private final String id;
    private final String cron;

    @JsonCreator
    public ScheduledTaskDTO(
            @JsonProperty(ID_PROPERTY_NAME) String id,
            @JsonProperty(CRON_PROPERTY_NAME) String cron
    ) {
        this.id = Objects.requireNonNull(id, ID_PROPERTY_NAME);
        this.cron = cron;
    }

    @JsonProperty(ID_PROPERTY_NAME)
    public String getId() {
        return id;
    }

    @JsonProperty(CRON_PROPERTY_NAME)
    public String getCron() {
        return cron;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledTaskDTO that = (ScheduledTaskDTO) o;
        return id.equals(that.id) && Objects.equals(cron, that.cron);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, cron);
    }
}
