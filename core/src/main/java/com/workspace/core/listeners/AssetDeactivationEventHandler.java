package com.workspace.core.listeners;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.event.jobs.JobManager;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        service = EventHandler.class,
        property = {
                EventConstants.EVENT_TOPIC
                        + "="
                        + ReplicationAction.EVENT_TOPIC
        }
)
public class AssetDeactivationEventHandler
        implements EventHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    AssetDeactivationEventHandler.class
            );

    private static final String DAM_ROOT =
            "/content/dam";

    private static final String JOB_TOPIC =
            "workspace/dam/deactivation/notification";

    @Reference
    private JobManager jobManager;

    @Override
    public void handleEvent(Event event) {

        LOG.info(
                "Replication event received. topic=[{}]",
                event.getTopic()
        );

        try {

            ReplicationAction action =
                    ReplicationAction.fromEvent(event);

            if (action == null) {

                LOG.warn(
                        "Unable to convert OSGi event into ReplicationAction."
                );

                return;
            }

            String path = action.getPath();

            String actionType =
                    action.getType() != null
                            ? action.getType().getName()
                            : null;

            String userId =
                    action.getUserId();

            long eventTime =
                    action.getTime();

            LOG.info(
                    "Replication action received. "
                            + "action=[{}], path=[{}], userId=[{}], time=[{}]",
                    actionType,
                    path,
                    userId,
                    eventTime
            );

            /*
             * Only DEACTIVATE is required.
             */
            if (action.getType()
                    != ReplicationActionType.DEACTIVATE) {

                LOG.debug(
                        "Ignoring replication action because it is "
                                + "not DEACTIVATE. action=[{}], path=[{}]",
                        actionType,
                        path
                );

                return;
            }

            /*
             * Only DAM paths are required.
             */
            if (path == null
                    || !path.startsWith(DAM_ROOT + "/")) {

                LOG.debug(
                        "Ignoring non-DAM deactivation. path=[{}]",
                        path
                );

                return;
            }

            /*
             * Generate stable event ID.
             */
            String eventId =
                    buildEventId(
                            action,
                            path
                    );

            LOG.info(
                    "Valid DAM deactivation detected. "
                            + "eventId=[{}], assetPath=[{}]",
                    eventId,
                    path
            );

            Map<String, Object> properties =
                    new HashMap<>();

            properties.put(
                    "eventId",
                    eventId
            );

            properties.put(
                    "assetPath",
                    path
            );

            properties.put(
                    "userId",
                    userId
            );

            properties.put(
                    "deactivationTime",
                    eventTime
            );

            /*
             * Create asynchronous Sling Job.
             */
            org.apache.sling.event.jobs.Job job =
                    jobManager.addJob(
                            JOB_TOPIC,
                            properties
                    );

            if (job == null) {

                LOG.error(
                        "Unable to create notification job. "
                                + "eventId=[{}], assetPath=[{}]",
                        eventId,
                        path
                );

                return;
            }

            LOG.info(
                    "Deactivation notification job created successfully. "
                            + "jobId=[{}], eventId=[{}], assetPath=[{}]",
                    job.getId(),
                    eventId,
                    path
            );

        } catch (Exception e) {

            LOG.error(
                    "Error while processing DAM deactivation event.",
                    e
            );
        }
    }

    private String buildEventId(
            ReplicationAction action,
            String path) {

        return action.getType().getName()
                + "-"
                + path
                + "-"
                + action.getTime()
                + "-"
                + String.valueOf(action.getUserId());
    }
}