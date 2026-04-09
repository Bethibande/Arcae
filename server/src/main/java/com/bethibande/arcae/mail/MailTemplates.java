package com.bethibande.arcae.mail;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate
public class MailTemplates {

    public static native TemplateInstance testMessage();

    public static native TemplateInstance passwordReset(final String token, final String username);

}
