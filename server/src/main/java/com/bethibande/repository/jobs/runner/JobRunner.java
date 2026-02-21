package com.bethibande.repository.jobs.runner;

import com.bethibande.repository.jobs.ScheduledJob;

public interface JobRunner {

    void run(final ScheduledJob job);

}
