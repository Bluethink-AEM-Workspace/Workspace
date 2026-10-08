package com.workspace.core.servlets;

import com.workspace.core.jobs.ElasticsearchDeadLetterJob;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.JobManager.QueryType;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/workspace/elasticsearch/replay",
                "sling.servlet.methods=POST"
        }
)
public class ElasticsearchReplayServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(ElasticsearchReplayServlet.class);

    private static final String ACTION_LIST = "list";
    private static final String ACTION_REPLAY = "replay";

    private static final String CONTENT_ROOT =
            "/content/workspace";

    @Reference
    private JobManager jobManager;

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String action = request.getParameter("action");

        if (action == null || action.trim().isEmpty()) {
            writeJson(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "{\"status\":\"ERROR\",\"message\":\"action is required. Use list or replay.\"}"
            );
            return;
        }

        if (ACTION_LIST.equalsIgnoreCase(action)) {
            listDlqJobs(response);
            return;
        }

        if (ACTION_REPLAY.equalsIgnoreCase(action)) {
            replayDlqJob(request, response);
            return;
        }

        writeJson(
                response,
                SlingHttpServletResponse.SC_BAD_REQUEST,
                "{\"status\":\"ERROR\",\"message\":\"Unsupported action. Use list or replay.\"}"
        );
    }

    private void listDlqJobs(
            SlingHttpServletResponse response)
            throws IOException {

        Collection<Job> jobs;

        try {
            jobs = jobManager.findJobs(
                    QueryType.QUEUED,
                    ElasticsearchDeadLetterJob.DLQ_TOPIC,
                    1000
            );
        } catch (Exception e) {

            LOG.error(
                    "event=es_dlq_list_failed message={}",
                    e.getMessage(),
                    e
            );

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"status\":\"ERROR\",\"message\":\"Unable to read DLQ jobs.\"}"
            );
            return;
        }

        StringBuilder json = new StringBuilder();

        json.append("{");
        json.append("\"status\":\"SUCCESS\",");
        json.append("\"count\":").append(jobs.size()).append(",");
        json.append("\"jobs\":[");

        boolean first = true;

        for (Job job : jobs) {

            String path = job.getProperty(
                    ElasticsearchDeadLetterJob.PATH,
                    String.class
            );

            String action = job.getProperty(
                    ElasticsearchDeadLetterJob.ACTION,
                    String.class
            );

            Boolean fromDlq = job.getProperty(
                    ElasticsearchDeadLetterJob.FROM_DLQ,
                    Boolean.class
            );

            Integer replayCount = job.getProperty(
                    ElasticsearchDeadLetterJob.REPLAY_COUNT,
                    Integer.class
            );

            String errorMessage = job.getProperty(
                    ElasticsearchDeadLetterJob.ERROR_MESSAGE,
                    String.class
            );

            Long failedAt = job.getProperty(
                    ElasticsearchDeadLetterJob.FAILED_AT,
                    Long.class
            );

            String originalTopic = job.getProperty(
                    ElasticsearchDeadLetterJob.ORIGINAL_TOPIC,
                    String.class
            );

            if (!first) {
                json.append(",");
            }

            json.append("{");
            json.append("\"jobId\":\"")
                    .append(escape(job.getId()))
                    .append("\",");

            json.append("\"path\":\"")
                    .append(escape(path))
                    .append("\",");

            json.append("\"action\":\"")
                    .append(escape(action))
                    .append("\",");

            json.append("\"fromDlq\":")
                    .append(Boolean.TRUE.equals(fromDlq));

            json.append(",");

            json.append("\"replayCount\":")
                    .append(replayCount == null ? 0 : replayCount);

            json.append(",");

            json.append("\"errorMessage\":\"")
                    .append(escape(errorMessage))
                    .append("\",");

            json.append("\"failedAt\":")
                    .append(failedAt == null ? 0 : failedAt);

            json.append(",");

            json.append("\"originalTopic\":\"")
                    .append(escape(originalTopic))
                    .append("\"");

            json.append("}");

            first = false;
        }

        json.append("]");
        json.append("}");

        LOG.info(
                "event=es_dlq_list count={}",
                jobs.size()
        );

        writeJson(
                response,
                SlingHttpServletResponse.SC_OK,
                json.toString()
        );
    }

    private void replayDlqJob(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws IOException {

        String path = request.getParameter("path");

        if (path == null || path.trim().isEmpty()) {

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "{\"status\":\"ERROR\",\"message\":\"path is required for replay.\"}"
            );
            return;
        }

        path = path.trim();

        if (!path.startsWith(CONTENT_ROOT + "/")) {

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_BAD_REQUEST,
                    "{\"status\":\"ERROR\",\"message\":\"Only /content/workspace paths can be replayed.\"}"
            );
            return;
        }

        Map<String, Object> template =
                new HashMap<String, Object>();

        template.put(
                ElasticsearchDeadLetterJob.PATH,
                path
        );

        Job dlqJob;

        try {
            dlqJob = jobManager.getJob(
                    ElasticsearchDeadLetterJob.DLQ_TOPIC,
                    template
            );
        } catch (Exception e) {

            LOG.error(
                    "event=es_replay_lookup_failed path={} message={}",
                    path,
                    e.getMessage(),
                    e
            );

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"status\":\"ERROR\",\"message\":\"Unable to find DLQ job.\"}"
            );
            return;
        }

        if (dlqJob == null) {

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_NOT_FOUND,
                    "{\"status\":\"ERROR\",\"message\":\"No queued DLQ job found for the requested path.\"}"
            );
            return;
        }

        String action = dlqJob.getProperty(
                ElasticsearchDeadLetterJob.ACTION,
                String.class
        );

        if (action == null || action.trim().isEmpty()) {
            action = "INDEX";
        }

        Integer replayCount = dlqJob.getProperty(
                ElasticsearchDeadLetterJob.REPLAY_COUNT,
                Integer.class
        );

        if (replayCount == null) {
            replayCount = 0;
        }

        Map<String, Object> replayProperties =
                new HashMap<String, Object>();

        replayProperties.put(
                "path",
                path
        );

        replayProperties.put(
                "action",
                action
        );

        replayProperties.put(
                "fromDlq",
                Boolean.TRUE
        );

        replayProperties.put(
                "replayCount",
                replayCount + 1
        );

        Job replayJob;

        try {
            replayJob = jobManager.addJob(
                    ElasticsearchDeadLetterJob.INDEX_TOPIC,
                    replayProperties
            );
        } catch (Exception e) {

            LOG.error(
                    "event=es_replay_create_failed path={} message={}",
                    path,
                    e.getMessage(),
                    e
            );

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"status\":\"ERROR\",\"message\":\"Unable to create replay job.\"}"
            );
            return;
        }

        if (replayJob == null) {

            LOG.error(
                    "event=es_replay_create_failed path={} message=jobManager.addJob returned null",
                    path
            );

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"status\":\"ERROR\",\"message\":\"Replay job could not be created.\"}"
            );
            return;
        }

        boolean removed;

        try {
            removed = jobManager.removeJobById(
                    dlqJob.getId()
            );
        } catch (Exception e) {

            LOG.error(
                    "event=es_replay_remove_failed path={} dlqJobId={} message={}",
                    path,
                    dlqJob.getId(),
                    e.getMessage(),
                    e
            );

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"status\":\"ERROR\",\"message\":\"Replay job created but old DLQ job could not be removed.\"}"
            );
            return;
        }

        if (!removed) {

            LOG.error(
                    "event=es_replay_remove_failed path={} dlqJobId={} message=removeJobById returned false",
                    path,
                    dlqJob.getId()
            );

            writeJson(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "{\"status\":\"ERROR\",\"message\":\"Replay job created but old DLQ job could not be removed.\"}"
            );
            return;
        }

        LOG.info(
                "event=es_replay_success path={} action={} replayCount={} dlqJobId={} replayJobId={}",
                path,
                action,
                replayCount + 1,
                dlqJob.getId(),
                replayJob.getId()
        );

        String json =
                "{"
                        + "\"status\":\"SUCCESS\","
                        + "\"message\":\"DLQ job replayed successfully.\","
                        + "\"path\":\"" + escape(path) + "\","
                        + "\"action\":\"" + escape(action) + "\","
                        + "\"replayCount\":" + (replayCount + 1) + ","
                        + "\"oldDlqJobId\":\"" + escape(dlqJob.getId()) + "\","
                        + "\"newJobId\":\"" + escape(replayJob.getId()) + "\""
                        + "}";

        writeJson(
                response,
                SlingHttpServletResponse.SC_OK,
                json
        );
    }

    private void writeJson(
            SlingHttpServletResponse response,
            int status,
            String json)
            throws IOException {

        response.setStatus(status);
        response.getWriter().write(json);
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}