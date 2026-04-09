package com.bethibande.arcae.jobs.runner;

import com.bethibande.arcae.jobs.ScheduledJob;

public interface JobRunner {

    void run(final ScheduledJob job);

}
