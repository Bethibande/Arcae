package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;

public interface JobTask<C> {

    Class<C> getConfigType();

    JobType getJobType();

    void run(final C config);

}
