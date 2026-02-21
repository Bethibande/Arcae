package com.bethibande.repository.jobs.impl;

import com.bethibande.repository.jobs.JobType;

public interface JobTask<C> {

    Class<C> getConfigType();

    JobType getJobType();

    void run(final C config);

}
