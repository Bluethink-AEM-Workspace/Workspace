
package com.workspace.core.listeners;

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

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;

@Component(
        service = EventHandler.class,
        immediate = true,
        property = {
                EventConstants.EVENT_TOPIC + "="
                        + ReplicationAction.EVENT_TOPIC
        }
)
public class AssetDeactivationEventHandler implements EventHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(AssetDeactivationEventHandler.class);

    @Reference
    private JobManager jobManager;

    @Override
    public void handleEvent(Event event) {

        LOG.info("Replication event received. topic={}", event.getTopic());

        ReplicationAction action =
                ReplicationAction.fromEvent(event);

        if (action == null) {
            LOG.warn("Replication action is null");
            return;
        }

        String path = action.getPath();
        String userId = action.getUserId();

        LOG.info("Replication action. type={}, path={}, user={}",
                action.getType(), path, userId);

        if (action.getType() != ReplicationActionType.DEACTIVATE) {
            LOG.debug("Ignoring replication action: {}", action.getType());
            return;
        }

        if (path == null || !path.startsWith("/content/dam/")) {
            LOG.debug("Ignoring non-DAM path: {}", path);
            return;
        }

        LOG.info("Valid DAM deactivation detected. path={}, user={}",
                path, userId);

        Map<String, Object> properties = new HashMap<>();
        properties.put("assetPath", path);
        properties.put("userId", userId);

        org.apache.sling.event.jobs.Job job =
                jobManager.addJob(
                        "workspace/dam/deactivation/notification",
                        properties);

        if (job != null) {
            LOG.info(
                    "Deactivation notification job created successfully. path={}, jobId={}",
                    path, job.getId());
        } else {
            LOG.error(
                    "Failed to create deactivation notification job. path={}",
                    path);
        }
    }
}