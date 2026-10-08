package com.workspace.core.jobs;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.workspace.core.services.ElasticsearchDocumentBuilder;
import com.workspace.core.services.ElasticsearchService;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = JobConsumer.class,
        property = {
                JobConsumer.PROPERTY_TOPICS
                        + "=workspace/elasticsearch/index"
        }
)
public class ElasticsearchIndexJob
        implements JobConsumer {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ElasticsearchIndexJob.class
            );

    private static final String TOPIC =
            "workspace/elasticsearch/index";

    private static final String DLQ_TOPIC =
            "workspace/elasticsearch/deadletter";

    private static final String PATH =
            "path";

    private static final String DELETE =
            "delete";

    private static final String RETRY_COUNT =
            "retryCount";

    private static final String FROM_DLQ =
            "fromDlq";

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ElasticsearchDocumentBuilder documentBuilder;

    @Reference
    private ElasticsearchService elasticsearchService;

    @Reference
    private JobManager jobManager;

    @Override
    public JobResult process(Job job) {

        String path =
                job.getProperty(
                        PATH,
                        String.class
                );

        Boolean delete =
                job.getProperty(
                        DELETE,
                        Boolean.class
                );

        Integer retryCount =
                job.getProperty(
                        RETRY_COUNT,
                        Integer.class
                );

        Boolean fromDlq =
                job.getProperty(
                        FROM_DLQ,
                        Boolean.class
                );

        if (path == null
                || path.trim().isEmpty()) {

            LOG.error(
                    "Elasticsearch index job cancelled because path is empty."
            );

            return JobResult.CANCEL;
        }

        if (retryCount == null) {
            retryCount = 0;
        }

        try {

            /*
             * DELETE must happen before Page lookup.
             */
            if (Boolean.TRUE.equals(delete)) {

                boolean deleted =
                        elasticsearchService.deleteDocument(
                                elasticsearchAlias(),
                                path
                        );

                if (!deleted) {
                    throw new IllegalStateException(
                            "Elasticsearch delete failed"
                    );
                }

                LOG.info(
                        "Deleted Elasticsearch document: {}",
                        path
                );

                return JobResult.OK;
            }

            /*
             * Open service resource resolver.
             */
            Map<String, Object> authInfo =
                    Collections.<String, Object>singletonMap(
                            ResourceResolverFactory.SUBSERVICE,
                            "elasticsearch-indexer"
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

                    throw new IllegalStateException(
                            "PageManager is unavailable"
                    );
                }

                Page page =
                        pageManager.getPage(
                                path
                        );

                /*
                 * Page no longer exists.
                 * Treat it as delete.
                 */
                if (page == null) {

                    boolean deleted =
                            elasticsearchService.deleteDocument(
                                    elasticsearchAlias(),
                                    path
                            );

                    if (!deleted) {
                        throw new IllegalStateException(
                                "Elasticsearch delete failed"
                        );
                    }

                    LOG.info(
                            "AEM page not found. Deleted Elasticsearch document: {}",
                            path
                    );

                    return JobResult.OK;
                }

                String document =
                        documentBuilder.build(
                                page
                        );

                boolean indexed =
                        elasticsearchService.indexDocument(
                                elasticsearchAlias(),
                                path,
                                document
                        );

                if (!indexed) {

                    throw new IllegalStateException(
                            "Elasticsearch indexing failed"
                    );
                }

                LOG.info(
                        "Indexed AEM page into Elasticsearch: {}",
                        path
                );

                return JobResult.OK;
            }

        } catch (Exception e) {

            LOG.error(
                    "Elasticsearch index job failed. Path: {}, RetryCount: {}",
                    path,
                    retryCount,
                    e
            );

            /*
             * For the first failure and DLQ retries,
             * put the job into the dead-letter flow.
             */
            if (retryCount < 3) {

                Map<String, Object> dlqProperties =
                        new HashMap<String, Object>();

                dlqProperties.put(
                        PATH,
                        path
                );

                dlqProperties.put(
                        DELETE,
                        Boolean.TRUE.equals(delete)
                );

                dlqProperties.put(
                        RETRY_COUNT,
                        retryCount
                );

                dlqProperties.put(
                        FROM_DLQ,
                        Boolean.TRUE.equals(fromDlq)
                );

                jobManager.addJob(
                        DLQ_TOPIC,
                        dlqProperties
                );

                LOG.warn(
                        "Elasticsearch job moved to DLQ. Path: {}, RetryCount: {}",
                        path,
                        retryCount
                );

                return JobResult.OK;
            }

            LOG.error(
                    "Elasticsearch indexing permanently failed after retries. Path: {}",
                    path
            );

            return JobResult.OK;
        }
    }

    private String elasticsearchAlias() {

        /*
         * The configured alias is the active write/search alias.
         */
        return "workspace-content";
    }
}