package com.workspace.core.listeners;

import com.day.cq.replication.ReplicationAction;
import com.workspace.core.services.impl.AlgoliaIndexingServiceImpl;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    service = EventHandler.class,
    immediate = true,
    property = {
        EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC
    }
)
public class AlgoliaReplicationEventHandler implements EventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AlgoliaReplicationEventHandler.class);

    @Reference
    private AlgoliaIndexingServiceImpl algoliaIndexingService;

    @Override
    public void handleEvent(Event event) {
        ReplicationAction action = ReplicationAction.fromEvent(event);
        if (action != null) {
            String path = action.getPath();
            LOG.info("=== ALGOLIA EVENT TRIGGERED === Path: {}, Type: {}", path, action.getType());

            if (path != null && path.startsWith("/content/workspace")) {
                
                // Exclude Language Masters Blueprint paths from indexing
                if (path.contains("/language-masters/")) {
                    LOG.info("Skipping indexing for Language Master path: {}", path);
                    return;
                }

                if (action.getType() != null && "ACTIVATE".equalsIgnoreCase(action.getType().getName())) {
                    if (algoliaIndexingService != null) {
                        algoliaIndexingService.indexPage(path);
                    }
                } else if (action.getType() != null && "DEACTIVATE".equalsIgnoreCase(action.getType().getName())) {
                    if (algoliaIndexingService != null) {
                        algoliaIndexingService.deletePage(path);
                    }
                }
            }
        }
    }
}