
package com.workspace.core.services.impl;

import java.util.List;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.workspace.core.services.DamAdminRecipientService;
import com.workspace.core.services.DeactivationEmailService;

@Component(service = DeactivationEmailService.class)
public class DeactivationEmailServiceImpl
        implements DeactivationEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DeactivationEmailServiceImpl.class);

    @Reference
    private DamAdminRecipientService recipientService;

    @Reference
    private MessageGatewayService messageGatewayService;

    @Override
public void sendEmail(String assetPath, String userId) {

    LOG.info(
            "Preparing deactivation email. path={}, user={}",
            assetPath, userId);

    List<String> recipients =
            recipientService.getRecipients();

    LOG.info("Notification recipients: {}", recipients);

    if (recipients == null || recipients.isEmpty()) {
        LOG.error("No DAM Admin email recipients configured");
        throw new IllegalStateException(
                "No DAM Admin email recipients configured");
    }

    MessageGateway<SimpleEmail> gateway =
            messageGatewayService.getGateway(SimpleEmail.class);

    if (gateway == null) {
        LOG.error("SimpleEmail MessageGateway is not available");
        throw new IllegalStateException(
                "Mail gateway is not available");
    }

    try {

        SimpleEmail email = new SimpleEmail();

        email.setSubject("DAM Asset Deactivated");

        email.setMsg(
                "A DAM asset has been deactivated.\n\n"
                + "Asset Path: " + assetPath + "\n"
                + "Deactivated By: " + userId + "\n"
                + "Status: Deactivated"
        );

        for (String recipient : recipients) {
            email.addTo(recipient);
        }

        gateway.send(email);

        LOG.info(
                "Deactivation email sent successfully. path={}, recipients={}",
                assetPath, recipients);

    } catch (EmailException e) {

        LOG.error(
                "Failed to send deactivation email. path={}",
                assetPath, e);

        throw new IllegalStateException(
                "Failed to send deactivation email", e);
    }
}
}