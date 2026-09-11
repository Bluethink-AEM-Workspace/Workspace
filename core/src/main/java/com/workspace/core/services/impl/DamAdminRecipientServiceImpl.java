package com.workspace.core.services.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workspace.core.services.DamAdminRecipientService;

@Component(service = DamAdminRecipientService.class)
@Designate(ocd = DamAdminRecipientServiceImpl.Config.class)
public class DamAdminRecipientServiceImpl
        implements DamAdminRecipientService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DamAdminRecipientServiceImpl.class);

    private String[] adminEmails = {};

    @ObjectClassDefinition(name = "Deactivation Notification Configuration")
    public @interface Config {

        @AttributeDefinition(
                name = "Admin Group Name",
                description = "DAM administrator group name")
        String adminGroupName() default "dam-administrators";

        @AttributeDefinition(
                name = "Email Template Path",
                description = "Path of the deactivation email template")
        String emailTemplatePath() default "/apps/workspace/mail-templates/deactivation-email.html";

        @AttributeDefinition(
                name = "Admin Email",
                description = "Email addresses of DAM administrators")
        String[] adminEmail() default {};
    }

    @Activate
    @Modified
    protected void activate(Config config) {

        adminEmails = config.adminEmail();

        LOG.info("DAM admin recipients configured: {}", adminEmails.length);
    }

    @Override
    public List<String> getRecipients() {

        LOG.info("Getting DAM admin recipients");

        if (adminEmails == null || adminEmails.length == 0) {
            LOG.error("No DAM admin emails are configured");
            return Collections.emptyList();
        }

        List<String> recipients = Arrays.stream(adminEmails)
                .filter(email -> email != null && !email.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        LOG.info("Number of valid DAM admin recipients: {}", recipients.size());

        return recipients;
    }
}