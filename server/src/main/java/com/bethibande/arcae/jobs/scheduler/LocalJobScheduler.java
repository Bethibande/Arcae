package com.bethibande.arcae.jobs.scheduler;

import com.bethibande.arcae.jobs.runner.JobRunner;
import com.bethibande.arcae.jobs.runner.LocalJobRunner;

import java.util.List;
import java.util.Objects;

public class LocalJobScheduler extends AbstractJobScheduler {

    private final LocalJobRunner localRunner;

    public LocalJobScheduler(final LocalJobRunner localRunner) {
        this.localRunner = localRunner;
    }

    @Override
    protected List<JobRunner> getWorkers() {
        return List.of(this.localRunner);
    }

    @Override
    protected JobRunner getWorkerByName(final String name) {
        return Objects.equals(name, this.localRunner.getName())
                ? this.localRunner
                : null;
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
