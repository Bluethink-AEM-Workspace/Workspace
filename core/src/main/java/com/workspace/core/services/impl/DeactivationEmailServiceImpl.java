package com.workspace.core.services.impl;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.workspace.core.models.DeactivationNotification;
import com.workspace.core.services.DeactivationEmailService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.mail.SimpleEmail;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = DeactivationEmailService.class
)
public class DeactivationEmailServiceImpl
        implements DeactivationEmailService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DeactivationEmailServiceImpl.class
            );

    private static final String SUBJECT =
            "[AEM DAM] Asset Deactivated - Confirmation Required";

    @Reference
    private MessageGatewayService messageGatewayService;

    @Override
    public void send(
            DeactivationNotification notification,
            List<String> recipients) {

        if (notification == null) {

            throw new IllegalArgumentException(
                    "Notification must not be null"
            );
        }

        if (recipients == null
                || recipients.isEmpty()) {

            throw new IllegalArgumentException(
                    "Email recipients must not be empty"
            );
        }

        LOG.info(
                "Preparing deactivation email. eventId=[{}], assetPath=[{}], recipients=[{}]",
                notification.getEventId(),
                notification.getAssetPath(),
                recipients.size()
        );

        try {

            MessageGateway<SimpleEmail> gateway =
                    messageGatewayService.getGateway(
                            SimpleEmail.class
                    );

            if (gateway == null) {

                LOG.error(
                        "No email MessageGateway available."
                );

                throw new IllegalStateException(
                        "Email MessageGateway is not available"
                );
            }

            SimpleEmail email =
                    new SimpleEmail();

            email.setSubject(SUBJECT);

            for (String recipient : recipients) {

                if (recipient == null
                        || recipient.trim().isEmpty()) {

                    continue;
                }

                try {

                    email.addTo(
                            new InternetAddress(
                                    recipient.trim()
                            ).getAddress()
                    );

                } catch (AddressException e) {

                    LOG.warn(
                            "Invalid email address ignored. email=[{}]",
                            recipient
                    );
                }
            }

            email.setMsg(
                    buildEmailBody(notification)
            );

            LOG.info(
                    "Sending deactivation email. eventId=[{}]",
                    notification.getEventId()
            );

            gateway.send(email);

            LOG.info(
                    "Deactivation email sent successfully. eventId=[{}]",
                    notification.getEventId()
            );

        } catch (Exception e) {

            LOG.error(
                    "Failed to send deactivation email. eventId=[{}]",
                    notification.getEventId(),
                    e
            );

            throw new RuntimeException(
                    "Unable to send deactivation email",
                    e
            );
        }
    }

    private String buildEmailBody(
            DeactivationNotification notification) {

        SimpleDateFormat format =
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss z"
                );

        format.setTimeZone(
                TimeZone.getTimeZone("Asia/Kolkata")
        );

        String userId =
                notification.getUserId();

        if (userId == null
                || userId.trim().isEmpty()) {

            userId = "Unknown";
        }

        return
                "AEM DAM Asset Deactivation Notification\n\n"
                        + "Asset Name: "
                        + notification.getAssetName()
                        + "\n\n"
                        + "Asset Path: "
                        + notification.getAssetPath()
                        + "\n\n"
                        + "Deactivated At: "
                        + format.format(
                                new Date(
                                        notification
                                                .getDeactivationTime()
                                )
                        )
                        + "\n\n"
                        + "Initiated By: "
                        + userId
                        + "\n\n"
                        + "Environment: "
                        + notification.getEnvironment()
                        + "\n\n"
                        + "Current Status: "
                        + notification.getCurrentStatus()
                        + "\n\n"
                        + "Requested Confirmation: "
                        + "Keep asset / proceed with configured cleanup process";
    }
}