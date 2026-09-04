package com.workspace.core.jobs;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.workspace.core.services.DeactivationEmailService;

@Component(
        service = JobConsumer.class,
        property = {
                JobConsumer.PROPERTY_TOPICS
                        + "=workspace/dam/deactivation/notification"
        }
)
public class AssetDeactivationNotificationJob implements JobConsumer {

    private static final Logger LOG =
            LoggerFactory.getLogger(AssetDeactivationNotificationJob.class);

    @Reference
    private DeactivationEmailService emailService;

    @Override
    public JobResult process(Job job) {

        String assetPath =
                (String) job.getProperty("assetPath");

        String userId =
                (String) job.getProperty("userId");

        LOG.info(
                "Processing deactivation job. path={}, user={}",
                assetPath, userId);

        if (assetPath == null || assetPath.trim().isEmpty()) {
            LOG.error("Asset path is missing from job");
            return JobResult.FAILED;
        }

        try {

            emailService.sendEmail(assetPath, userId);

            LOG.info(
                    "Deactivation email process completed. path={}",
                    assetPath);

            return JobResult.OK;

        } catch (Exception e) {

            LOG.error(
                    "Failed to send deactivation email. path={}",
                    assetPath, e);

            return JobResult.FAILED;
        }
    }
}