package com.bethibande.repository.jpa;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.LockModeType;
import org.wildfly.security.util.PasswordUtil;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
public class TaskLock extends PanacheEntity {

    public static TaskLock acquire(final String taskId, final Instant now, final long timeout, final ChronoUnit unit) {
        final TaskLock lock = TaskLock.find("taskId = ?1", taskId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();

        final Instant timeoutEnd = now.plus(timeout, unit);
        if (lock == null) {
            final TaskLock newLock = new TaskLock();
            newLock.taskId = taskId;
            newLock.doNotExecuteBefore = now;
            newLock.tryAcquire(now, timeoutEnd);
            newLock.persist();

            return newLock;
        }

        return lock.tryAcquire(now, timeoutEnd)
                ? lock
                : null;
    }

    public static void release(final String taskId, final String lock, final Instant now) {
        final TaskLock lockEntity = TaskLock.find("taskId = ?1", taskId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
        lockEntity.tryRelease(lock, now);
    }

    public static final CronDefinition CRON_DEFINITION = CronDefinitionBuilder.defineCron()
            .withSeconds().and()
            .withMinutes().and()
            .withHours().and()
            .withDayOfMonth().and()
            .withMonth().and()
            .withDayOfWeek().and()
            .instance();

    @Column(nullable = false, unique = true, columnDefinition = "varchar(255)")
    public String taskId;

    @Column(nullable = false, columnDefinition = "timestamptz")
    public Instant doNotExecuteBefore;

    @Column(columnDefinition = "varchar(255)")
    public String lock;

    @Column(columnDefinition = "timestamptz")
    public Instant lockEnd;

    @Column(columnDefinition = "timestamptz")
    public Instant lastSuccess;

    public void scheduleFor(final Instant nextExecution) {
        this.doNotExecuteBefore = nextExecution;
    }

    public void scheduleForCron(final String cronValue) {
        final CronParser parser = new CronParser(CRON_DEFINITION);
        final Cron cron = parser.parse(cronValue);
        final ExecutionTime executionTime = ExecutionTime.forCron(cron);

        final Instant now = Instant.now();
        this.doNotExecuteBefore = executionTime.nextExecution(now.atZone(ZoneOffset.UTC))
                .orElseThrow()
                .toInstant();
    }

    public boolean isRunning(final Instant now) {
        return this.lock != null
                && this.lockEnd.isAfter(now);
    }

    public boolean canAcquire(final Instant now) {
        return !isRunning(now) && this.doNotExecuteBefore.isBefore(now);
    }

    public boolean tryAcquire(final Instant now, final Instant lockEnd) {
        if (canAcquire(now)) {
            setLock(PasswordUtil.generateSecureRandomString(16), lockEnd);
            return true;
        }
        return false;
    }

    public void setLock(final String lock, final Instant lockEnd) {
        this.lock = lock;
        this.lockEnd = lockEnd;
    }

    public void tryRelease(final String lock, final Instant now) {
        if (!Objects.equals(this.lock, lock)) return;

        this.lock = null;
        this.lockEnd = null;
        this.lastSuccess = now;
    }

}
