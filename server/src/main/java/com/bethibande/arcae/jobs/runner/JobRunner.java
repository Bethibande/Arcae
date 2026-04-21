package com.bethibande.arcae.jobs.runner;

import com.bethibande.arcae.jobs.ScheduledJob;

import java.time.Instant;

public interface JobRunner {

    String getName();

    Instant getStartedAt();

    void run(final long jobId) throws RunnerQueueCapacityReached;

}
