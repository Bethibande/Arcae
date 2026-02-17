package com.bethibande.repository.jobs;

import com.bethibande.process.annotation.EntityDTO;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.Type;

import java.time.Instant;

@Entity
@EntityDTO(excludeProperties = {"id", "nextRunAt", "runner"})
public class ScheduledJob extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(64)")
    public JobType type;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public String settings;

    @Column(nullable = false, columnDefinition = "varchar(64)")
    public String cronSchedule;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant nextRunAt;

    @Column(nullable = false)
    public boolean deleteAfterRun;

    @Column(columnDefinition = "varchar(256)")
    public String runner;


    public boolean isRunning() {
        return this.runner != null;
    }

}
