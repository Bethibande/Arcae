package com.bethibande.repository.jobs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LocalJobRunner {

    @Inject
    protected JobScheduler scheduler;

    public void run(final ScheduledJob job) {
        // TODO
    }

}
