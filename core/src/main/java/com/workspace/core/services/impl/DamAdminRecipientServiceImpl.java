

package com.workspace.core.services.impl;

import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
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
            LoggerFactory.getLogger(
                    DamAdminRecipientServiceImpl.class);

    private String adminEmail;

    @ObjectClassDefinition(
            name = "DAM Admin Recipient Configuration"
    )
    public @interface Config {

        @AttributeDefinition(name = "Admin Email")
        String adminEmail();
    }

    @Activate
    protected void activate(Config config) {

        adminEmail = config.adminEmail();

        LOG.info("DAM admin email configured: {}", adminEmail);
    }

    @Override
    public List<String> getRecipients() {

        LOG.info("Getting DAM admin recipient");

        if (adminEmail == null || adminEmail.trim().isEmpty()) {
            LOG.error("DAM admin email is not configured");
            return Collections.emptyList();
        }

        return Collections.singletonList(adminEmail.trim());
    }
}   