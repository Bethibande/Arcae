package com.bethibande.arcae.jobs.scheduler;

import com.bethibande.arcae.jobs.ScheduledJob;

import java.time.Instant;

public interface JobScheduler {

    Instant getStartupTime();

    boolean isActive();

    void schedule(final ScheduledJob job);

    void runNow(final ScheduledJob job);

    void complete(final ScheduledJob job);

    void fail(final long jobId);

}
