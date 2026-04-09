package com.bethibande.arcae.web.api;

import com.bethibande.arcae.mail.MailConnectionSettings;
import com.bethibande.arcae.mail.MailerService;
import com.bethibande.arcae.mail.SMTPConfig;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

@RolesAllowed("ADMIN")
@Path("/api/v1/mail")
public class MailEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailEndpoint.class);

    @Inject
    protected MailerService mailerService;

    @GET
    @Path("/config")
    public @NotNull SMTPConfig getConfig() {
        return this.mailerService.getConfig();
    }

    @PUT
    @Path("/config")
    public void updateConfig(final SMTPConfig config) {
        this.mailerService.updateConfig(config);
    }

    @PUT
    @RolesAllowed("SYSTEM")
    @Path("/config/update")
    public void updateConfigFromDatabase() {
        this.mailerService.updateConfigFromDatabase();
    }

    @GET
    @Path("/autodiscover")
    public CompletionStage<MailConnectionSettings> autoDiscover(final @QueryParam("from") String fromMail) {
        return this.mailerService.autoDiscover(fromMail);
    }

    public record TestMailResult(
            boolean success,
            String error
    ) {
    }

    @PUT
    @Path("/test")
    public CompletionStage<TestMailResult> sendTestMessage(final @QueryParam("to") String toMail) {
        return this.mailerService.sendTestMessage(toMail)
                .thenApply(_ -> new TestMailResult(true, null))
                .exceptionally(ex -> {
                    LOGGER.error("Failed to send test mail", ex);
                    return new TestMailResult(false, ex.getCause().getMessage());
                });
    }

}
