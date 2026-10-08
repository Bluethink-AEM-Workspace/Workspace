package com.workspace.core.handlers;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.replication.ReplicationEvent;

import org.apache.sling.event.jobs.JobManager;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component(
        service = EventHandler.class,
        property = {
                EventConstants.EVENT_TOPIC + "="
                        + ReplicationEvent.EVENT_TOPIC
        }
)
public class ElasticsearchReplicationHandler
        implements EventHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ElasticsearchReplicationHandler.class
            );

    private static final String JOB_TOPIC =
            "workspace/elasticsearch/index";

    private static final String CONTENT_ROOT =
            "/content/workspace";

    @Reference
    private JobManager jobManager;

    @Override
    public void handleEvent(Event event) {

        ReplicationEvent replicationEvent =
                ReplicationEvent.fromEvent(event);

        if (replicationEvent == null) {
            return;
        }

        ReplicationAction action =
                replicationEvent.getReplicationAction();

        if (action == null) {
            return;
        }

        String path =
                action.getPath();

        if (path == null
                || path.trim().isEmpty()) {
            return;
        }

        /*
         * Only Workspace content should be indexed.
         */
        if (!isWorkspaceContent(path)) {

            LOG.debug(
                    "Ignoring replication event outside Elasticsearch content root: {}",
                    path
            );

            return;
        }

        ReplicationActionType actionType =
                action.getType();

        if (ReplicationActionType.ACTIVATE.equals(
                actionType
        )) {

            enqueueJob(
                    path,
                    false,
                    0
            );

            LOG.info(
                    "Activation queued for Elasticsearch: {}",
                    path
            );

        } else if (
                ReplicationActionType.DEACTIVATE.equals(
                        actionType
                )
        ) {

            enqueueJob(
                    path,
                    true,
                    0
            );

            LOG.info(
                    "Deactivation queued for Elasticsearch: {}",
                    path
            );
        }
    }

    private void enqueueJob(
            String path,
            boolean delete,
            int retryCount
    ) {

        Map<String, Object> properties =
                new HashMap<String, Object>();

        properties.put(
                "path",
                path
        );

        properties.put(
                "delete",
                delete
        );

        properties.put(
                "retryCount",
                retryCount
        );

        properties.put(
                "fromDlq",
                false
        );

        jobManager.addJob(
                JOB_TOPIC,
                properties
        );
    }

    private boolean isWorkspaceContent(
            String path
    ) {

        return path.equals(CONTENT_ROOT)
                || path.startsWith(
                        CONTENT_ROOT + "/"
                );
    }
}