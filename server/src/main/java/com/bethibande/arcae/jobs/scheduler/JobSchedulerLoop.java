package com.bethibande.arcae.jobs.scheduler;

import io.quarkus.arc.ClientProxy;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JobSchedulerLoop {

    @Inject
    protected JobScheduler scheduler;

    @Scheduled(cron = "0 * * * * ?")
    public void loop() {
        if (ClientProxy.unwrap(scheduler) instanceof AbstractJobScheduler base) {
            base.loop();
        }
    }

}
