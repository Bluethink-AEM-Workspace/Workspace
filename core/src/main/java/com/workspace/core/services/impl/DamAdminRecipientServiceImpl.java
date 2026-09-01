package com.workspace.core.services.impl;

import com.workspace.core.services.DamAdminRecipientService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = DamAdminRecipientService.class
)
@Designate(
        ocd = DamAdminRecipientServiceImpl.Config.class
)
public class DamAdminRecipientServiceImpl
        implements DamAdminRecipientService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DamAdminRecipientServiceImpl.class
            );

    private volatile List<String> recipients =
            Collections.emptyList();

    @ObjectClassDefinition(
            name = "Workspace DAM Admin Recipients"
    )
    public @interface Config {

        @AttributeDefinition(
                name = "DAM Admin Email Addresses",
                description =
                        "Email addresses that receive DAM deactivation notifications"
        )
        String[] recipients() default {};
    }

    @Activate
    @Modified
    protected void activate(Config config) {

        List<String> configured =
                new ArrayList<>();

        for (String email : config.recipients()) {

            if (email != null
                    && !email.trim().isEmpty()) {

                configured.add(
                        email.trim()
                );
            }
        }

        recipients =
                Collections.unmodifiableList(
                        configured
                );

        LOG.info(
                "DAM Admin recipients configured. count=[{}]",
                recipients.size()
        );
    }

    @Override
    public List<String> getRecipients() {

        LOG.debug(
                "Returning DAM Admin recipients. count=[{}]",
                recipients.size()
        );

        return recipients;
    }
}