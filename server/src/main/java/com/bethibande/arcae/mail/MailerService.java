package com.bethibande.arcae.mail;

import com.bethibande.arcae.jpa.system.SystemProperty;
import com.bethibande.arcae.k8s.KubernetesSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.SrvRecord;
import io.vertx.ext.mail.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Startup
@ApplicationScoped
public class MailerService {

    public static final String SMTP_PROPERTY_NAME = "mail.smtp";

    @Inject
    protected ObjectMapper objectMapper;

    @Inject
    protected Vertx vertx;

    @Inject
    protected KubernetesSupport kubernetesSupport;

    private volatile SMTPConfig config;
    private volatile MailClient mailClient;

    private DnsClient dnsClient;

    @Transactional
    @PostConstruct
    protected void init() {
        this.dnsClient = vertx.createDnsClient();

        this.config = SystemProperty.get(SMTP_PROPERTY_NAME, SMTPConfig.class, this.objectMapper);
        if (this.config == null) {
            this.config = new SMTPConfig(
                    false,
                    null,
                    null,
                    null,
                    25,
                    null
            );
        }

        if (this.config.enabled()) {
            this.mailClient = createMailClient(this.config);
        }
    }

    private MailClient createMailClient(final SMTPConfig config) {
        final MailConfig mailConfig = new MailConfig(
                config.host(),
                config.port()
        );

        mailConfig.setStarttls(
                config.encryption() == MailEncryption.START_TLS
                        ? StartTLSOptions.REQUIRED
                        : StartTLSOptions.DISABLED
        );
        mailConfig.setSsl(config.encryption() == MailEncryption.SSL);

        mailConfig.setUsername(config.from());
        mailConfig.setPassword(config.password());

        return MailClient.create(vertx, mailConfig);
    }

    public CompletionStage<MailResult> sendTestMessage(final String to) {
        final MailMessage message = createMessage();
        message.setTo(to);
        message.setSubject("Test mail");

        final String body = MailTemplates.testMessage()
                .render();
        message.setHtml(body);

        return sendMessage(message);
    }

    public MailMessage createMessage() {
        final MailMessage message = new MailMessage();
        message.setFrom(this.config.from());

        return message;
    }

    public CompletionStage<MailResult> sendMessage(final MailMessage message) {
        if (!mailerEnabled()) throw new IllegalStateException("Mailer is not enabled");

        return this.mailClient.sendMail(message)
                .toCompletionStage();
    }

    private CompletionStage<MailConnectionSettings> discoverHost(final String dns, final MailEncryption tls) {
        return this.dnsClient.resolveSRV(dns)
                .toCompletionStage()
                .thenApply(records -> {
                    records.sort(Comparator.comparing(SrvRecord::priority).thenComparing(SrvRecord::weight));

                    if (records.isEmpty()) return null;
                    final SrvRecord record = records.getFirst();

                    return new MailConnectionSettings(
                            record.target(),
                            record.port(),
                            tls
                    );
                });
    }

    public CompletionStage<MailConnectionSettings> autoDiscover(final String mail) {
        final String host = mail.split("@")[1];

        return discoverHost("_submission._tcp.%s".formatted(host), MailEncryption.START_TLS)
                .thenCompose(result -> {
                    if (result == null) {
                        return discoverHost("_submissions._tcp.%s".formatted(host), MailEncryption.SSL);
                    }

                    return CompletableFuture.completedFuture(result);
                });
    }

    public boolean mailerEnabled() {
        return config != null && config.enabled();
    }

    public void updateConfigFromDatabase() {
        final SMTPConfig config = SystemProperty.get(SMTP_PROPERTY_NAME, SMTPConfig.class, this.objectMapper);
        if (config != null) {
            this.config = config;
            this.mailClient = config.enabled() ? createMailClient(config) : null;
        }
    }

    public void updateConfig(final SMTPConfig config) {
        QuarkusTransaction.requiringNew().run(() -> {
            SystemProperty.set(SMTP_PROPERTY_NAME, config, this.objectMapper);
        });

        this.config = config;
        this.mailClient = config.enabled() ? createMailClient(config) : null;

        if (this.kubernetesSupport.isServiceDiscoveryEnabled()) {
            this.kubernetesSupport.broadcastHttp(
                    (baseURL, webClient) -> webClient.putAbs(baseURL + "/api/v1/mail/config/update").send()
            );
        }
    }

    public SMTPConfig getConfig() {
        return config;
    }
}
