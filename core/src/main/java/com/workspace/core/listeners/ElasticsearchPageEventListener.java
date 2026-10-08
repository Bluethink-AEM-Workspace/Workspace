package com.workspace.core.listeners;

import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(
        service = ResourceChangeListener.class,
        immediate = true,
        property = {
                ResourceChangeListener.PATHS + "=/content/workspace",
                ResourceChangeListener.CHANGES + "=ADDED",
                ResourceChangeListener.CHANGES + "=CHANGED",
                ResourceChangeListener.CHANGES + "=REMOVED"
        }
)
public class ElasticsearchPageEventListener
        implements ResourceChangeListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ElasticsearchPageEventListener.class
            );

    private static final String CONTENT_ROOT =
            "/content/workspace";

    /*
     * Job topic consumed by ElasticsearchIncrementalIndexJob
     */
    public static final String JOB_TOPIC =
            "workspace/elasticsearch/incremental";

    public static final String PROPERTY_ACTION =
            "action";

    public static final String PROPERTY_PATH =
            "path";

    public static final String PROPERTY_DESTINATION =
            "destination";

    private static final String ACTION_INDEX =
            "INDEX";

    private static final String ACTION_DELETE =
            "DELETE";

    @Reference
    private JobManager jobManager;

    @Override
    public void onChange(List<ResourceChange> changes) {

        if (changes == null || changes.isEmpty()) {
            return;
        }

        LOG.info(
                "Elasticsearch ResourceChangeListener received {} change(s)",
                changes.size()
        );

        for (ResourceChange change : changes) {

            if (change == null) {
                continue;
            }

            try {

                processChange(change);

            } catch (Exception e) {

                LOG.error(
                        "Error processing Elasticsearch resource change. Path: {}",
                        change.getPath(),
                        e
                );
            }
        }
    }

    private void processChange(
            ResourceChange change) {

        String resourcePath =
                change.getPath();

        ResourceChange.ChangeType type =
                change.getType();

        if (isBlank(resourcePath)
                || !isWorkspacePath(resourcePath)) {

            return;
        }

        String pagePath =
                resolvePagePath(resourcePath);

        if (isBlank(pagePath)
                || CONTENT_ROOT.equals(pagePath)) {

            return;
        }

        LOG.info(
                "AEM resource change received. Type: {}, Resource path: {}, Page path: {}",
                type,
                resourcePath,
                pagePath
        );

        switch (type) {

            case ADDED:
            case CHANGED:

                /*
                 * Any page/resource content change can affect the
                 * Elasticsearch document, so re-index the page.
                 */
                addIndexJob(pagePath);
                break;

            case REMOVED:

                /*
                 * If jcr:content or a child component/resource is removed,
                 * the page still exists and must be re-indexed.
                 *
                 * If the page node itself is removed, delete the
                 * Elasticsearch document.
                 */
                if (isPageNodePath(resourcePath)) {

                    addDeleteJob(pagePath);

                } else {

                    addIndexJob(pagePath);
                }

                break;

            default:

                LOG.debug(
                        "Ignoring unsupported resource change type: {} for {}",
                        type,
                        resourcePath
                );
        }
    }

    /**
     * Converts a changed resource path into the corresponding AEM page path.
     *
     * Examples:
     *
     * /content/workspace/us/elasticsearch
     *     -> /content/workspace/us/elasticsearch
     *
     * /content/workspace/us/elasticsearch/jcr:content
     *     -> /content/workspace/us/elasticsearch
     *
     * /content/workspace/us/elasticsearch/jcr:content/root/text
     *     -> /content/workspace/us/elasticsearch
     */
    private String resolvePagePath(
            String resourcePath) {

        if (resourcePath == null) {
            return null;
        }

        int jcrContentIndex =
                resourcePath.indexOf("/jcr:content");

        if (jcrContentIndex >= 0) {

            return resourcePath.substring(
                    0,
                    jcrContentIndex
            );
        }

        return resourcePath;
    }

    /**
     * Determines whether the removed resource is the page node itself.
     *
     * A removal below jcr:content means page content/component was removed,
     * therefore the page should be re-indexed instead of deleting its document.
     */
    private boolean isPageNodePath(
            String resourcePath) {

        return resourcePath != null
                && resourcePath.startsWith(
                CONTENT_ROOT + "/"
        )
                && !resourcePath.contains(
                "/jcr:content"
        );
    }

    private void addIndexJob(
            String pagePath) {

        addJob(
                ACTION_INDEX,
                pagePath,
                null
        );
    }

    private void addDeleteJob(
            String pagePath) {

        addJob(
                ACTION_DELETE,
                pagePath,
                null
        );
    }

    private void addJob(
            String action,
            String path,
            String destination) {

        try {

            Map<String, Object> properties =
                    new HashMap<>();

            properties.put(
                    PROPERTY_ACTION,
                    action
            );

            properties.put(
                    PROPERTY_PATH,
                    path
            );

            if (!isBlank(destination)) {

                properties.put(
                        PROPERTY_DESTINATION,
                        destination
                );
            }

            Job job =
                    jobManager.addJob(
                            JOB_TOPIC,
                            properties
                    );

            if (job != null) {

                LOG.info(
                        "Elasticsearch incremental job created. Action: {}, Path: {}",
                        action,
                        path
                );

            } else {

                LOG.error(
                        "Unable to create Elasticsearch incremental job. Action: {}, Path: {}",
                        action,
                        path
                );
            }

        } catch (Exception e) {

            LOG.error(
                    "Failed to create Elasticsearch incremental job. Action: {}, Path: {}",
                    action,
                    path,
                    e
            );
        }
    }

    private boolean isWorkspacePath(
            String path) {

        return CONTENT_ROOT.equals(path)
                || path.startsWith(
                CONTENT_ROOT + "/"
        );
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }
}