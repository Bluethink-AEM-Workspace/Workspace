package com.workspace.core.jobs;

import com.workspace.core.listeners.ElasticsearchPageEventListener;
import com.workspace.core.services.ElasticsearchService;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;

import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.sling.api.resource.ValueMap;

@Component(
        service = JobConsumer.class,
        immediate = true,
        property = {
                JobConsumer.PROPERTY_TOPICS + "=" +
                        ElasticsearchPageEventListener.JOB_TOPIC
        }
)
public class ElasticsearchIncrementalIndexJob
        implements JobConsumer {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ElasticsearchIncrementalIndexJob.class
            );

    private static final String CONTENT_ROOT =
            "/content/workspace";

    /*
     * Keep this value unchanged.
     */
    private static final String SUBSERVICE =
            "elasticsearch-indexer";

    private static final String ACTION_INDEX =
            "INDEX";

    private static final String ACTION_DELETE =
            "DELETE";

    /*
     * Dead-letter queue topic.
     *
     * There is intentionally NO JobConsumer for this topic.
     * DLQ jobs must remain queued until replayed explicitly.
     */
    private static final String DLQ_TOPIC =
            "workspace/elasticsearch/deadletter";

    private static final String ORIGINAL_TOPIC =
            "workspace/elasticsearch/incremental";

    private static final String PROPERTY_ACTION =
            "action";

    private static final String PROPERTY_PATH =
            "path";

    private static final String PROPERTY_FROM_DLQ =
            "fromDlq";

    private static final String PROPERTY_REPLAY_COUNT =
            "replayCount";

    private static final String PROPERTY_ERROR_MESSAGE =
            "errorMessage";

    private static final String PROPERTY_FAILED_AT =
            "failedAt";

    private static final String PROPERTY_ORIGINAL_TOPIC =
            "originalTopic";

    @Reference
    private ElasticsearchService elasticsearchService;

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private JobManager jobManager;

    @Override
    public JobResult process(
            Job job) {

        if (job == null) {

            LOG.error(
                    "event=es_incremental_invalid_job message=job_null"
            );

            return JobResult.CANCEL;
        }

        String action =
                job.getProperty(
                        ElasticsearchPageEventListener.PROPERTY_ACTION,
                        String.class
                );

        String path =
                job.getProperty(
                        ElasticsearchPageEventListener.PROPERTY_PATH,
                        String.class
                );

        Boolean fromDlq =
                job.getProperty(
                        PROPERTY_FROM_DLQ,
                        Boolean.class
                );

        Integer replayCount =
                job.getProperty(
                        PROPERTY_REPLAY_COUNT,
                        Integer.class
                );

        if (replayCount == null) {
            replayCount = 0;
        }

        if (isBlank(action)
                || isBlank(path)) {

            LOG.error(
                    "event=es_incremental_invalid_job action={} path={}",
                    action,
                    path
            );

            return JobResult.CANCEL;
        }

        if (!isWorkspacePath(path)) {

            LOG.warn(
                    "event=es_incremental_ignored action={} path={} reason=outside_workspace",
                    action,
                    path
            );

            return JobResult.CANCEL;
        }

        LOG.info(
                "event=es_incremental_start action={} path={} fromDlq={} replayCount={}",
                action,
                path,
                Boolean.TRUE.equals(fromDlq),
                replayCount
        );

        if (!elasticsearchService.isEnabled()) {

            LOG.error(
                    "event=es_incremental_failure action={} path={} reason=elasticsearch_disabled",
                    action,
                    path
            );

            boolean dlqCreated =
                    sendToDlq(
                            path,
                            action,
                            Boolean.TRUE.equals(fromDlq),
                            replayCount,
                            "Elasticsearch service is disabled"
                    );

            return dlqCreated
                    ? JobResult.OK
                    : JobResult.FAILED;
        }

        try {

            if (ACTION_DELETE.equals(action)) {

                return processDelete(
                        path,
                        Boolean.TRUE.equals(fromDlq),
                        replayCount
                );
            }

            if (ACTION_INDEX.equals(action)) {

                return processIndex(
                        path,
                        Boolean.TRUE.equals(fromDlq),
                        replayCount
                );
            }

            LOG.error(
                    "event=es_incremental_failure action={} path={} reason=unknown_action",
                    action,
                    path
            );

            return JobResult.CANCEL;

        } catch (Exception e) {

            LOG.error(
                    "event=es_incremental_exception action={} path={} message={}",
                    action,
                    path,
                    e.getMessage(),
                    e
            );

            boolean dlqCreated =
                    sendToDlq(
                            path,
                            action,
                            Boolean.TRUE.equals(fromDlq),
                            replayCount,
                            e.getMessage()
                    );

            return dlqCreated
                    ? JobResult.OK
                    : JobResult.FAILED;
        }
    }

    /* =========================================================
       PROCESS INDEX
       ========================================================= */

    private JobResult processIndex(
            String pagePath,
            boolean fromDlq,
            int replayCount)
            throws Exception {

        Map<String, Object> authInfo =
                Collections.singletonMap(
                        ResourceResolverFactory.SUBSERVICE,
                        SUBSERVICE
                );

        try (
                ResourceResolver resolver =
                        resourceResolverFactory
                                .getServiceResourceResolver(
                                        authInfo
                                )
        ) {

            PageManager pageManager =
                    resolver.adaptTo(
                            PageManager.class
                    );

            if (pageManager == null) {

                LOG.error(
                        "event=es_incremental_failure action=INDEX path={} reason=page_manager_null",
                        pagePath
                );

                return sendFailureToDlq(
                        pagePath,
                        ACTION_INDEX,
                        fromDlq,
                        replayCount,
                        "Unable to obtain PageManager"
                );
            }

            Page page =
                    pageManager.getPage(
                            pagePath
                    );

            /*
             * Page may have been deleted between event and job execution.
             * In that case remove its Elasticsearch document.
             */
            if (page == null) {

                LOG.info(
                        "event=es_incremental_page_missing path={} action=DELETE",
                        pagePath
                );

                boolean deleted =
                        elasticsearchService.deleteDocument(
                                elasticsearchService.getIndexAlias(),
                                pagePath
                        );

                if (deleted) {

                    LOG.info(
                            "event=es_incremental_success action=DELETE path={} fromDlq={} replayCount={}",
                            pagePath,
                            fromDlq,
                            replayCount
                    );

                    return JobResult.OK;
                }

                return sendFailureToDlq(
                        pagePath,
                        ACTION_DELETE,
                        fromDlq,
                        replayCount,
                        "Elasticsearch deleteDocument returned false"
                );
            }

            String document =
                    createDocument(
                            page
                    );

            if (isBlank(document)) {

                return sendFailureToDlq(
                        pagePath,
                        ACTION_INDEX,
                        fromDlq,
                        replayCount,
                        "Could not create Elasticsearch document"
                );
            }

            boolean success =
                    elasticsearchService.indexDocument(
                            elasticsearchService.getIndexAlias(),
                            pagePath,
                            document
                    );

            if (success) {

                LOG.info(
                        "event=es_incremental_success action=INDEX path={} fromDlq={} replayCount={}",
                        pagePath,
                        fromDlq,
                        replayCount
                );

                return JobResult.OK;
            }

            return sendFailureToDlq(
                    pagePath,
                    ACTION_INDEX,
                    fromDlq,
                    replayCount,
                    "Elasticsearch indexDocument returned false"
            );
        }
    }

    /* =========================================================
       PROCESS DELETE
       ========================================================= */

    private JobResult processDelete(
            String pagePath,
            boolean fromDlq,
            int replayCount) {

        boolean success =
                elasticsearchService.deleteDocument(
                        elasticsearchService.getIndexAlias(),
                        pagePath
                );

        if (success) {

            LOG.info(
                    "event=es_incremental_success action=DELETE path={} fromDlq={} replayCount={}",
                    pagePath,
                    fromDlq,
                    replayCount
            );

            return JobResult.OK;
        }

        return sendFailureToDlq(
                pagePath,
                ACTION_DELETE,
                fromDlq,
                replayCount,
                "Elasticsearch deleteDocument returned false"
        );
    }

    /* =========================================================
       DLQ HANDLING
       ========================================================= */

    private JobResult sendFailureToDlq(
            String path,
            String action,
            boolean fromDlq,
            int replayCount,
            String errorMessage) {

        boolean dlqCreated =
                sendToDlq(
                        path,
                        action,
                        fromDlq,
                        replayCount,
                        errorMessage
                );

        return dlqCreated
                ? JobResult.OK
                : JobResult.FAILED;
    }

    private boolean sendToDlq(
            String path,
            String action,
            boolean fromDlq,
            int replayCount,
            String errorMessage) {

        try {

            Map<String, Object> properties =
                    new HashMap<>();

            properties.put(
                    PROPERTY_PATH,
                    path
            );

            properties.put(
                    PROPERTY_ACTION,
                    action
            );

            properties.put(
                    PROPERTY_FROM_DLQ,
                    fromDlq
            );

            properties.put(
                    PROPERTY_REPLAY_COUNT,
                    replayCount
            );

            properties.put(
                    PROPERTY_ERROR_MESSAGE,
                    isBlank(errorMessage)
                            ? "Unknown Elasticsearch indexing failure"
                            : errorMessage
            );

            properties.put(
                    PROPERTY_FAILED_AT,
                    Instant.now().toString()
            );

            properties.put(
                    PROPERTY_ORIGINAL_TOPIC,
                    ORIGINAL_TOPIC
            );

            Job dlqJob =
                    jobManager.addJob(
                            DLQ_TOPIC,
                            properties
                    );

            if (dlqJob == null) {

                LOG.error(
                        "event=es_dlq_create_failed path={} action={} fromDlq={} replayCount={}",
                        path,
                        action,
                        fromDlq,
                        replayCount
                );

                return false;
            }

            LOG.error(
                    "event=es_dlq_created path={} action={} fromDlq={} replayCount={} errorMessage={}",
                    path,
                    action,
                    fromDlq,
                    replayCount,
                    errorMessage
            );

            return true;

        } catch (Exception e) {

            LOG.error(
                    "event=es_dlq_exception path={} action={} message={}",
                    path,
                    action,
                    e.getMessage(),
                    e
            );

            return false;
        }
    }

    /* =========================================================
       DOCUMENT CREATION
       ========================================================= */

    private String createDocument(
            Page page) {

        if (page == null) {
            return null;
        }

        ValueMap properties =
                page.getProperties();

        String title =
                getString(
                        properties,
                        "jcr:title"
                );

        if (isBlank(title)) {
            title = page.getTitle();
        }

        String description =
                getString(
                        properties,
                        "jcr:description"
                );

        String template =
                getString(
                        properties,
                        "cq:template"
                );

        String contentType =
                getString(
                        properties,
                        "sling:resourceType"
                );

        String[] tags =
                properties.get(
                        "cq:tags",
                        String[].class
                );

        String content =
                extractText(
                        page.getContentResource()
                );

        Calendar lastModified =
                properties.get(
                        "cq:lastModified",
                        Calendar.class
                );

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        json.append(
                "\"id\":\""
        );

        json.append(
                escapeJson(
                        page.getPath()
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"path\":\""
        );

        json.append(
                escapeJson(
                        page.getPath()
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"title\":\""
        );

        json.append(
                escapeJson(
                        title
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"description\":\""
        );

        json.append(
                escapeJson(
                        description
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"content\":\""
        );

        json.append(
                escapeJson(
                        content
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"template\":\""
        );

        json.append(
                escapeJson(
                        template
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"contentType\":\""
        );

        json.append(
                escapeJson(
                        contentType
                )
        );

        json.append(
                "\","
        );

        json.append(
                "\"tags\":["
        );

        if (tags != null) {

            for (
                    int i = 0;
                    i < tags.length;
                    i++
            ) {

                if (i > 0) {
                    json.append(",");
                }

                json.append("\"");

                json.append(
                        escapeJson(
                                tags[i]
                        )
                );

                json.append("\"");
            }
        }

        json.append("]");

        if (lastModified != null) {

            json.append(
                    ",\"lastModified\":\""
            );

            json.append(
                    escapeJson(
                            lastModified
                                    .toInstant()
                                    .toString()
                    )
            );

            json.append("\"");
        }

        json.append("}");

        return json.toString();
    }

    /* =========================================================
       TEXT EXTRACTION
       ========================================================= */

    private String extractText(
            Resource resource) {

        if (resource == null) {
            return "";
        }

        StringBuilder text =
                new StringBuilder();

        collectText(
                resource,
                text
        );

        return text
                .toString()
                .trim();
    }

    private void collectText(
            Resource resource,
            StringBuilder text) {

        if (resource == null) {
            return;
        }

        ValueMap properties =
                resource.getValueMap();

        for (
                String propertyName :
                properties.keySet()
        ) {

            Object value =
                    properties.get(
                            propertyName
                    );

            if (value instanceof String) {

                String stringValue =
                        (String) value;

                if (!stringValue
                        .trim()
                        .isEmpty()) {

                    if (text.length() > 0) {
                        text.append(" ");
                    }

                    text.append(
                            stringValue
                    );
                }
            }
        }

        for (
                Resource child :
                resource.getChildren()
        ) {

            collectText(
                    child,
                    text
            );
        }
    }

    private String getString(
            ValueMap properties,
            String property) {

        if (properties == null) {
            return "";
        }

        String value =
                properties.get(
                        property,
                        String.class
                );

        return value == null
                ? ""
                : value;
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

    private String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }
}