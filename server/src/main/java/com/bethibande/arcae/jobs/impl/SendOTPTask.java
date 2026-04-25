package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jpa.user.OneTimePassword;
import com.bethibande.arcae.jpa.user.TwoFASession;
import com.bethibande.arcae.mail.MailTemplates;
import com.bethibande.arcae.mail.MailerService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.ext.mail.MailMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SendOTPTask implements JobTask<SendOTPTask.Config> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendOTPTask.class);

    @RegisterForReflection
    public record Config(
            long sessionId
    ) {
    }

    @Inject
    protected MailerService mailerService;

    @Override
    public Class<Config> getConfigType() {
        return Config.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.SEND_OTP;
    }

    @Override
    public void run(final Config config) {
        final MailMessage finalMessage = QuarkusTransaction.requiringNew().call(() -> {
            final TwoFASession session = TwoFASession.findById(config.sessionId);
            if (session == null) {
                LOGGER.warn("Requested OTP for non-existent session: {}", config.sessionId);
                return null;
            }

            final OneTimePassword password = OneTimePassword.generate(session);

            final MailMessage message = this.mailerService.createMessage();
            message.setTo(password.session.user.email);
            message.setSubject("One time password");
            message.setHtml(MailTemplates.loginOTP(password.code, password.session.user.name).render());

            return message;
        });

        if (finalMessage != null) {
            this.mailerService.sendMessage(finalMessage);
        }
    }
}
