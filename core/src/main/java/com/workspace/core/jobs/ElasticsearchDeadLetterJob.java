package com.workspace.core.jobs;

/**
 * Elasticsearch Dead Letter Queue constants.
 *
 * This class intentionally does NOT implement JobConsumer.
 *
 * DLQ jobs must remain queued until they are explicitly replayed
 * by ElasticsearchReplayServlet.
 */
public final class ElasticsearchDeadLetterJob {

    private ElasticsearchDeadLetterJob() {
        // Utility class
    }

    /**
     * Sling Job topic used for failed Elasticsearch operations.
     */
    public static final String DLQ_TOPIC =
            "workspace/elasticsearch/deadletter";

    /**
     * Original incremental indexing job topic.
     */
    public static final String INDEX_TOPIC =
            "workspace/elasticsearch/incremental";

    /**
     * DLQ job property containing the AEM page path.
     */
    public static final String PATH =
            "path";

    /**
     * DLQ job property containing the failed operation.
     *
     * Possible values:
     * INDEX
     * DELETE
     */
    public static final String ACTION =
            "action";

    /**
     * Indicates that the job was created from DLQ replay.
     */
    public static final String FROM_DLQ =
            "fromDlq";

    /**
     * Number of times this DLQ item has been replayed.
     */
    public static final String REPLAY_COUNT =
            "replayCount";

    /**
     * Error/failure message stored in DLQ.
     */
    public static final String ERROR_MESSAGE =
            "errorMessage";

    /**
     * Timestamp when the item was moved to DLQ.
     */
    public static final String FAILED_AT =
            "failedAt";

    /**
     * Original Sling Job topic from which the failure came.
     */
    public static final String ORIGINAL_TOPIC =
            "originalTopic";
}