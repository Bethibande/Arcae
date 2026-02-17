package com.bethibande.repository.web.api;

import com.bethibande.repository.jobs.ScheduledJob;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/api/v1/job")
public class JobEndpoint {

    @POST
    @Path("/schedule")
    public ScheduledJob scheduleJob() {
        return new ScheduledJob(); // TODO
    }

}
