package com.bethibande.arcae.jobs.impl;

import com.bethibande.arcae.jobs.JobType;
import com.bethibande.arcae.jpa.user.PasswordResetToken;
import com.bethibande.arcae.jpa.user.User;
import com.bethibande.arcae.mail.MailTemplates;
import com.bethibande.arcae.mail.MailerService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.ext.mail.MailMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.wildfly.security.util.PasswordUtil;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class PasswordResetTask implements JobTask<PasswordResetTask.Config> {

    private final MailerService mailerService;

    @Inject
    public PasswordResetTask(final MailerService mailerService) {
        this.mailerService = mailerService;
    }

    @RegisterForReflection
    public record Config(String email) {
    }

    @Override
    public Class<Config> getConfigType() {
        return Config.class;
    }

    @Override
    public JobType getJobType() {
        return JobType.RESET_PASSWORD;
    }

    @Override
    public void run(final Config config) {
        final PasswordResetToken token = QuarkusTransaction.requiringNew().call(() -> {
            final User user = User.find("email = ?1", config.email()).firstResult();

            if (user == null) return null;

            final long resetCount = PasswordResetToken.count("user = ?1", user);
            if (resetCount > 3) return null;

            final PasswordResetToken entity = new PasswordResetToken();
            entity.user = user;
            entity.token = PasswordUtil.generateSecureRandomString(9);
            entity.expiration = Instant.now().plus(Duration.ofMinutes(15));

            entity.persist();

            return entity;
        });

        if (token == null) return;

        final MailMessage message = this.mailerService.createMessage();
        message.setTo(config.email());
        message.setSubject("Password reset");
        message.setHtml(MailTemplates.passwordReset(token.token, token.user.name).render());
        
        this.mailerService.sendMessage(message);
    }
}
