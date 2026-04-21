package com.bethibande.arcae.jobs.runner;

public class RunnerQueueCapacityReached extends Exception {
    public RunnerQueueCapacityReached(String message) {
        super(message);
    }
}
