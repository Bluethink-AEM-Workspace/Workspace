/*

AssetExpiryNotificationService is responsible for sending the generated DAM asset expiry report to configured recipients through AEM’s Day CQ Mail Service and `MessageGateway`. It builds a multipart email with the configured subject and environment details, attaches the generated CSV report, validates the recipients and report file, and hands the email over to AEM’s mail gateway for delivery. The service also handles email configuration through OSGi and provides appropriate logging and error handling without managing the sender address directly, as the From Address is controlled by Day CQ Mail Service.

*/




package com.workspace.core.services;

import java.io.File;

import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailAttachment;
import org.apache.commons.mail.MultiPartEmail;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;

@Component(service = AssetExpiryNotificationService.class)
@Designate(ocd = AssetExpiryNotificationService.Config.class)
public class AssetExpiryNotificationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    AssetExpiryNotificationService.class);

    @Reference
    private MessageGatewayService messageGatewayService;

    private volatile String environment;
    private volatile String authorUrl;
    private volatile String subjectTemplate;

    @ObjectClassDefinition(
            name = "Workspace Asset Expiry Email Configuration",
            description =
                    "Email configuration for asset expiry report notifications")
    public @interface Config {

        @AttributeDefinition(
                name = "Environment",
                description =
                        "Environment name included in the email")
        String environment() default "author";

        @AttributeDefinition(
                name = "AEM Author URL",
                description =
                        "Base URL used to construct authenticated asset links")
        String authorUrl() default "http://localhost:4502";

        @AttributeDefinition(
                name = "Email Subject",
                description =
                        "Subject used for the asset expiry report email")
        String subjectTemplate() default
                "DAM Asset Expiry Report";
    }

    @Activate
    @Modified
    protected void activate(Config config) {

        environment =
                config.environment();

        authorUrl =
                removeTrailingSlash(
                        config.authorUrl());

        subjectTemplate =
                config.subjectTemplate();

        LOG.info(
                "Asset expiry email configuration loaded. " +
                "Environment: {}, Author URL: {}",
                environment,
                authorUrl);
    }

    /**
     * Sends the generated CSV expiry report through
     * AEM Day CQ Mail Service.
     *
     * The From Address is deliberately not configured here.
     * It is controlled by Day CQ Mail Service.
     *
     * @param reportFile generated CSV report
     * @param recipients recipient email addresses
     * @param assetCount number of assets included in report
     * @return true when successfully handed to MessageGateway
     */
    public boolean sendExpiryReport(
            File reportFile,
            String[] recipients,
            int assetCount) {

        if (reportFile == null
                || !reportFile.exists()
                || !reportFile.isFile()) {

            LOG.error(
                    "Cannot send expiry report because report file does not exist: {}",
                    reportFile);

            return false;
        }

        if (recipients == null
                || recipients.length == 0) {

            LOG.error(
                    "Cannot send expiry report because no recipients were configured");

            return false;
        }

        try {

            /*
             * ---------------------------------------------------------
             * STEP 1: Build the email
             * ---------------------------------------------------------
             *
             * MultiPartEmail is required because the email contains
             * a file attachment.
             *
             * The From Address is deliberately NOT set here.
             * Day CQ Mail Service provides the sender address.
             */

            MultiPartEmail email =
                    new MultiPartEmail();

            int validRecipientCount = 0;

            StringBuilder recipientLog =
                    new StringBuilder();

            for (String recipient : recipients) {

                if (recipient != null
                        && !recipient.trim().isEmpty()) {

                    String trimmedRecipient =
                            recipient.trim();

                    email.addTo(
                            trimmedRecipient);

                    if (validRecipientCount > 0) {
                        recipientLog.append(", ");
                    }

                    recipientLog.append(
                            trimmedRecipient);

                    validRecipientCount++;
                }
            }

            if (validRecipientCount == 0) {

                LOG.error(
                        "No valid email recipients available for expiry report");

                return false;
            }

            email.setSubject(
                    subjectTemplate);

            String message =
                    buildReportMessage(
                            assetCount);

            email.setMsg(
                    message);

            /*
             * ---------------------------------------------------------
             * STEP 2: Attach the generated CSV report
             * ---------------------------------------------------------
             */

            EmailAttachment attachment =
                    new EmailAttachment();

            attachment.setPath(
                    reportFile.getAbsolutePath());

            attachment.setName(
                    "workspace-asset-expiry-report.csv");

            attachment.setDescription(
                    "DAM asset expiry report");

            attachment.setDisposition(
                    EmailAttachment.ATTACHMENT);

            email.attach(
                    attachment);

            LOG.info(
                    "Expiry report email prepared successfully. " +
                    "Recipients: {}, Asset count: {}, File: {}",
                    recipientLog.toString(),
                    assetCount,
                    reportFile.getAbsolutePath());

            LOG.info(
                    "Expiry notification sender will be provided " +
                    "by Day CQ Mail Service");

            /*
             * ---------------------------------------------------------
             * STEP 3: Obtain AEM MessageGateway
             * ---------------------------------------------------------
             */

            LOG.info(
                    "Attempting to obtain MessageGateway for expiry report email");

            MessageGateway<Email> messageGateway =
                    messageGatewayService
                            .getGateway(Email.class);

            if (messageGateway == null) {

                LOG.error(
                        "No MessageGateway available for expiry report email. " +
                        "Email was prepared but was NOT sent.");

                return false;
            }

            LOG.info(
                    "MessageGateway successfully obtained. " +
                    "Sending expiry report email now.");

            /*
             * ---------------------------------------------------------
             * STEP 4: Send email
             * ---------------------------------------------------------
             */

            messageGateway.send(
                    email);

            /*
             * ---------------------------------------------------------
             * STEP 5: Confirm successful handoff
             * ---------------------------------------------------------
             */

            LOG.info(
                    "Expiry report email successfully handed to " +
                    "MessageGateway. Recipients: {}, Assets: {}",
                    recipientLog.toString(),
                    assetCount);

            return true;

        } catch (Exception e) {

            LOG.error(
                    "Failed to send asset expiry report email",
                    e);

            return false;
        }
    }

    /**
     * Builds the email body for the CSV report.
     *
     * @param assetCount number of assets included in report
     * @return email message
     */
    private String buildReportMessage(
            int assetCount) {

        return "Hello,\n\n"
                + "Please find attached the DAM asset expiry report.\n\n"
                + "Environment: "
                + environment
                + "\n"
                + "Assets included in report: "
                + assetCount
                + "\n\n"
                + "The attached CSV contains the asset name, "
                + "asset path, expiration date and Author link.\n\n"
                + "Please review the report and take the necessary action.\n\n"
                + "Regards,\n"
                + "Workspace AEM";
    }

    /**
     * Removes trailing slash characters from a URL.
     *
     * @param value configured URL
     * @return URL without trailing slash
     */
    private String removeTrailingSlash(
            String value) {

        if (value == null) {
            return "";
        }

        String result =
                value.trim();

        while (result.endsWith("/")) {

            result =
                    result.substring(
                            0,
                            result.length() - 1);
        }

        return result;
    }
}

