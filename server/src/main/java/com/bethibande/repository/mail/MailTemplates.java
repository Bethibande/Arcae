package com.bethibande.repository.mail;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate
public class MailTemplates {

    public static native TemplateInstance testMessage(final int currentYear);

}
