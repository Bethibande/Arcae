package com.bethibande.repository.jobs;

import com.bethibande.process.annotation.EntityDTO;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.Type;

import java.time.Instant;

@Entity
@EntityDTO
@EntityDTO(excludeProperties = {"id", "nextRunAt", "runner"}, name = "ScheduledJobDTOWithoutId")
@RegisterForReflection(targets = {JobType.class, ScheduledJobDTO.class, ScheduledJobDTOWithoutId.class})
public class ScheduledJob extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(64)")
    public JobType type;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public String settings;

    @Column(nullable = false, columnDefinition = "varchar(64)")
    public String cronSchedule;

    @Column(columnDefinition = "timestamptz")
    public Instant nextRunAt;

    @Column(nullable = false)
    public boolean deleteAfterRun;

    @Column(columnDefinition = "varchar(256)")
    public String runner;

    @Column(columnDefinition = "timestamptz")
    public Instant lastSuccessfulRun;


    public boolean isRunning() {
        return this.runner != null;
    }

}
