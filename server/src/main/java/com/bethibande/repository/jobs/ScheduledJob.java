package com.bethibande.repository.jobs;

import com.bethibande.process.annotation.EntityDTO;
import com.bethibande.process.annotation.VirtualDTOField;
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
    public Instant executionStartedAt;

    @Column(columnDefinition = "timestamptz")
    public Instant lastSuccessfulRun;

    @VirtualDTOField
    public JobStatus getStatus() {
        return getStatus(Instant.now());
    }

    public JobStatus getStatus(final Instant now) {
        if (this.runner != null && this.executionStartedAt != null) {
            return JobStatus.RUNNING;
        }
        if (runner == null
                && this.executionStartedAt != null
                && this.nextRunAt != null
                && !this.executionStartedAt.isBefore(this.nextRunAt)) return JobStatus.FAILED;

        if (this.nextRunAt != null) {
            if (!now.isBefore(this.nextRunAt)) return JobStatus.QUEUED;
            return JobStatus.SCHEDULED;
        }
        return JobStatus.IDLE;
    }

}
