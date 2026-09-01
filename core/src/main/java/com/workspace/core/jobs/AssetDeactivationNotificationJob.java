package com.workspace.core.jobs;

import com.day.cq.dam.api.Asset;
import com.workspace.core.models.DeactivationNotification;
import com.workspace.core.services.DamAdminRecipientService;
import com.workspace.core.services.DeactivationEmailService;
import com.workspace.core.services.DeactivationNotificationAuditService;

import java.util.Collections;
import java.util.List;

import org.apache.sling.api.resource.*;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = JobConsumer.class,
    property = JobConsumer.PROPERTY_TOPICS +
        "=workspace/dam/deactivation/notification"
)
public class AssetDeactivationNotificationJob implements JobConsumer {

    private static final Logger LOG =
        LoggerFactory.getLogger(AssetDeactivationNotificationJob.class);

    private static final String SUBSERVICE = "damNotificationService";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private DamAdminRecipientService recipientService;

    @Reference
    private DeactivationEmailService emailService;

    @Reference
    private DeactivationNotificationAuditService auditService;

    @Override
    public JobResult process(Job job) {

        String eventId = str(job, "eventId");
        String assetPath = str(job, "assetPath");
        String userId = str(job, "userId");
        Long time = number(job, "deactivationTime");

        LOG.info("Processing deactivation job. eventId={}, path={}",
                eventId, assetPath);

        if (blank(eventId) || blank(assetPath)) {
            LOG.error("Invalid job properties. eventId={}, path={}",
                    eventId, assetPath);
            return JobResult.CANCEL;
        }

        try {
            if (auditService.isAlreadySent(eventId)) {
                LOG.info("Duplicate event skipped. eventId={}", eventId);
                return JobResult.OK;
            }

            if (time == null)
                time = System.currentTimeMillis();

            try (ResourceResolver resolver = getResolver()) {

                Resource resource = resolver.getResource(assetPath);

                if (resource == null) {
                    LOG.warn("Asset not found. path={}", assetPath);
                    return JobResult.OK;
                }

                Asset asset = resource.adaptTo(Asset.class);

                if (asset == null) {
                    LOG.warn("Unable to adapt asset. path={}", assetPath);
                    return JobResult.OK;
                }

                List<String> recipients =
                    recipientService.getRecipients();

                    LOG.info("Notification recipients: {}", recipients);

                if (recipients == null || recipients.isEmpty()) {
                    LOG.error("No DAM Admin recipients.");
                    auditService.markFailed(
                        eventId, assetPath, "No recipients");
                    return JobResult.FAILED;
                }

                DeactivationNotification notification =
                    new DeactivationNotification(
                        eventId,
                        asset.getName(),
                        assetPath,
                        time,
                        userId,
                        "Deactivated",
                        "Author"
                    );

                LOG.info("Sending email. eventId={}", eventId);

                emailService.send(notification, recipients);

                auditService.markSent(
                    eventId, assetPath, time);

                LOG.info("Notification completed. eventId={}", eventId);

                return JobResult.OK;
            }

        } catch (Exception e) {

            LOG.error("Deactivation job failed. eventId={}",
                    eventId, e);

            auditService.markFailed(
                eventId,
                assetPath,
                e.getMessage()
            );

            return JobResult.FAILED;
        }
    }

    private ResourceResolver getResolver()
            throws LoginException {

        return resolverFactory.getServiceResourceResolver(
            Collections.singletonMap(
                ResourceResolverFactory.SUBSERVICE,
                SUBSERVICE
            )
        );
    }

    private String str(Job job, String name) {
        Object value = job.getProperty(name);
        return value == null ? null : value.toString();
    }

    private Long number(Job job, String name) {
        Object value = job.getProperty(name);
        if (value instanceof Number)
            return ((Number) value).longValue();

        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (Exception e) {
            LOG.warn("Invalid number property: {}", name);
            return null;
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}