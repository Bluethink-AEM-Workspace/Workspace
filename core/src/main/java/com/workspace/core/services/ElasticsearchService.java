package com.workspace.core.services;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
// import org.osgi.service.metatype.annotations.ComponentPropertyType;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import java.util.Base64;

@Component(service = ElasticsearchService.class)
@Designate(ocd = ElasticsearchService.Config.class)
public class ElasticsearchService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ElasticsearchService.class);

    private static final String BULK_PATH = "/_bulk";
    private static final String ALIASES_PATH = "/_aliases";

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_SEARCH_SIZE = 100;
    private static final int MAX_AUTOCOMPLETE_SIZE = 20;

    /*
     * Retry configuration
     *
     * Attempt 1 -> 500 ms
     * Attempt 2 -> 1000 ms
     * Attempt 3 -> final failure
     */
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 500L;

    private String serverUrl;
    private String username;
    private String password;

    private String indexAlias;
    private String indexPattern;
    private String indexTemplateName;

    private int numberOfShards;
    private int numberOfReplicas;

    private int connectTimeout;
    private int readTimeout;

    private boolean enabled;
    private boolean trustAllSsl;

    /* =========================================================
       ACTIVATE
       ========================================================= */

    @Activate
    protected void activate(Config config) {

        enabled = config.enabled();

        serverUrl = removeTrailingSlash(config.serverUrl());
        username = config.username();
        password = config.password();

        indexAlias = config.indexAlias();
        indexPattern = config.indexPattern();
        indexTemplateName = config.indexTemplateName();

        numberOfShards = config.numberOfShards();
        numberOfReplicas = config.numberOfReplicas();

        connectTimeout = config.connectTimeout();
        readTimeout = config.readTimeout();

        trustAllSsl = config.trustAllSsl();

        LOG.info(
                "Elasticsearch service started. enabled={}, server={}, alias={}",
                enabled,
                serverUrl,
                indexAlias
        );
    }

    @Deactivate
    protected void deactivate() {

        LOG.info(
                "Elasticsearch service stopped"
        );
    }

    /* =========================================================
       BASIC GETTERS
       ========================================================= */

    public boolean isEnabled() {

        return enabled;
    }

    public String getIndexAlias() {

        return indexAlias;
    }

    /* =========================================================
       HEALTH CHECK
       ========================================================= */

    public boolean isHealthy() {

        if (!enabled) {

            LOG.warn(
                    "Elasticsearch is disabled"
            );

            return false;
        }

        try {

            HttpResponse response =
                    requestWithRetry(
                            "GET",
                            "/",
                            null
                    );

            boolean healthy =
                    isSuccess(
                            response.statusCode
                    );

            if (healthy) {

                LOG.info(
                        "Elasticsearch health check successful"
                );

            } else {

                LOG.error(
                        "Elasticsearch health check failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );
            }

            return healthy;

        } catch (Exception e) {

            LOG.error(
                    "Elasticsearch health check exception",
                    e
            );

            return false;
        }
    }

    /* =========================================================
       INDEX TEMPLATE
       ========================================================= */

    public boolean createIndexTemplate() {

        if (!enabled) {

            return false;
        }

        if (isBlank(indexTemplateName)
                || isBlank(indexPattern)) {

            LOG.error(
                    "Index template name or pattern is empty"
            );

            return false;
        }

        try {

            HttpResponse response =
                    requestWithRetry(
                            "PUT",
                            "/_index_template/" +
                                    encodePath(
                                            indexTemplateName
                                    ),
                            buildIndexTemplateBody()
                    );

            if (isSuccess(response.statusCode)) {

                LOG.info(
                        "Elasticsearch index template created: {}",
                        indexTemplateName
                );

                return true;
            }

            LOG.error(
                    "Index template creation failed. status={}, body={}",
                    response.statusCode,
                    response.body
            );

            return false;

        } catch (Exception e) {

            LOG.error(
                    "Index template creation exception",
                    e
            );

            return false;
        }
    }

    private String buildIndexTemplateBody() {

        return "{"
                + "\"index_patterns\":[\"" +
                escapeJson(indexPattern) +
                "\"],"

                + "\"template\":{"

                + "\"settings\":{"

                + "\"number_of_shards\":" +
                numberOfShards +
                ","

                + "\"number_of_replicas\":" +
                numberOfReplicas +
                ","

                + "\"analysis\":{"

                + "\"filter\":{"

                + "\"workspace_edge_ngram\":{"
                + "\"type\":\"edge_ngram\","
                + "\"min_gram\":2,"
                + "\"max_gram\":20"
                + "}"

                + "},"

                + "\"analyzer\":{"

                + "\"workspace_text\":{"
                + "\"type\":\"custom\","
                + "\"tokenizer\":\"standard\","
                + "\"filter\":["
                + "\"lowercase\","
                + "\"asciifolding\""
                + "]"
                + "},"

                + "\"workspace_autocomplete\":{"
                + "\"type\":\"custom\","
                + "\"tokenizer\":\"standard\","
                + "\"filter\":["
                + "\"lowercase\","
                + "\"asciifolding\","
                + "\"workspace_edge_ngram\""
                + "]"
                + "}"

                + "}"

                + "}"

                + "},"

                + "\"mappings\":" +
                buildMappings()

                + "}"

                + "}";
    }

    /* =========================================================
       CREATE INDEX
       ========================================================= */

    public boolean createIndex(
            String indexName) {

        if (!enabled
                || isBlank(indexName)) {

            LOG.error(
                    "Cannot create index. Invalid configuration."
            );

            return false;
        }

        try {

            HttpResponse response =
                    requestWithRetry(
                            "PUT",
                            "/" +
                                    encodePath(indexName),
                            buildIndexBody()
                    );

            if (isSuccess(response.statusCode)) {

                LOG.info(
                        "Elasticsearch index created: {}",
                        indexName
                );

                return true;
            }

            if (response.statusCode == 400
                    && response.body != null
                    && response.body.contains(
                            "resource_already_exists_exception"
            )) {

                LOG.info(
                        "Elasticsearch index already exists: {}",
                        indexName
                );

                return true;
            }

            LOG.error(
                    "Index creation failed. index={}, status={}, body={}",
                    indexName,
                    response.statusCode,
                    response.body
            );

            return false;

        } catch (Exception e) {

            LOG.error(
                    "Index creation exception: {}",
                    indexName,
                    e
            );

            return false;
        }
    }

    private String buildIndexBody() {

        return "{"

                + "\"settings\":{"

                + "\"number_of_shards\":" +
                numberOfShards +
                ","

                + "\"number_of_replicas\":" +
                numberOfReplicas +
                ","

                + "\"analysis\":{"

                + "\"filter\":{"

                + "\"workspace_edge_ngram\":{"
                + "\"type\":\"edge_ngram\","
                + "\"min_gram\":2,"
                + "\"max_gram\":20"
                + "}"

                + "},"

                + "\"analyzer\":{"

                + "\"workspace_text\":{"
                + "\"type\":\"custom\","
                + "\"tokenizer\":\"standard\","
                + "\"filter\":["
                + "\"lowercase\","
                + "\"asciifolding\""
                + "]"
                + "},"

                + "\"workspace_autocomplete\":{"
                + "\"type\":\"custom\","
                + "\"tokenizer\":\"standard\","
                + "\"filter\":["
                + "\"lowercase\","
                + "\"asciifolding\","
                + "\"workspace_edge_ngram\""
                + "]"
                + "}"

                + "}"

                + "}"

                + "},"

                + "\"mappings\":" +
                buildMappings()

                + "}";
    }

    /* =========================================================
       EXPLICIT MAPPINGS
       ========================================================= */

    private String buildMappings() {

        return "{"

                + "\"dynamic\":false,"

                + "\"properties\":{"

                + "\"id\":{"
                + "\"type\":\"keyword\""
                + "},"

                + "\"path\":{"
                + "\"type\":\"keyword\""
                + "},"

                + "\"title\":{"
                + "\"type\":\"text\","
                + "\"analyzer\":\"workspace_text\","

                + "\"fields\":{"

                + "\"raw\":{"
                + "\"type\":\"keyword\","
                + "\"ignore_above\":256"
                + "},"

                + "\"autocomplete\":{"
                + "\"type\":\"text\","
                + "\"analyzer\":\"workspace_autocomplete\","
                + "\"search_analyzer\":\"workspace_text\""
                + "}"

                + "}"

                + "},"

                + "\"description\":{"
                + "\"type\":\"text\","
                + "\"analyzer\":\"workspace_text\""
                + "},"

                + "\"content\":{"
                + "\"type\":\"text\","
                + "\"analyzer\":\"workspace_text\""
                + "},"

                + "\"template\":{"
                + "\"type\":\"keyword\""
                + "},"

                + "\"contentType\":{"
                + "\"type\":\"keyword\""
                + "},"

                + "\"tags\":{"
                + "\"type\":\"keyword\","
                + "\"ignore_above\":256"
                + "},"

                + "\"lastModified\":{"
                + "\"type\":\"date\""
                + "}"

                + "}"

                + "}";
    }

    /* =========================================================
       CREATE VERSIONED INDEX
       ========================================================= */

    public String createVersionedIndex() {

        if (!enabled) {

            LOG.error(
                    "Cannot create versioned index because " +
                            "Elasticsearch is disabled"
            );

            return null;
        }

        /*
         * Template create karne ki koshish.
         * Template fail hone par explicit mappings
         * ke saath index creation continue hoga.
         */
        if (!createIndexTemplate()) {

            LOG.warn(
                    "Index template creation failed. " +
                            "Continuing with explicit mappings."
            );
        }

        String indexName =
                indexAlias +
                        "-" +
                        System.currentTimeMillis();

        LOG.info(
                "Creating versioned Elasticsearch index: {}",
                indexName
        );

        if (createIndex(indexName)) {

            LOG.info(
                    "Versioned index created successfully: {}",
                    indexName
            );

            return indexName;
        }

        LOG.error(
                "Unable to create versioned Elasticsearch index: {}",
                indexName
        );

        return null;
    }

    /* =========================================================
       DELETE INDEX
       ========================================================= */

    public boolean deleteIndex(
            String indexName) {

        if (!enabled
                || isBlank(indexName)) {

            return false;
        }

        try {

            HttpResponse response =
                    requestWithRetry(
                            "DELETE",
                            "/" +
                                    encodePath(indexName),
                            null
                    );

            return isSuccess(response.statusCode)
                    || response.statusCode == 404;

        } catch (Exception e) {

            LOG.error(
                    "Delete index failed: {}",
                    indexName,
                    e
            );

            return false;
        }
    }

    /* =========================================================
       INDEX DOCUMENT
       ========================================================= */

    public boolean indexDocument(
            String indexName,
            String documentId,
            String json) {

        if (!enabled
                || isBlank(indexName)
                || isBlank(documentId)
                || isBlank(json)) {

            return false;
        }

        try {

            String path =
                    "/" +
                            encodePath(indexName) +
                            "/_doc/" +
                            encodePath(documentId);

            HttpResponse response =
                    requestWithRetry(
                            "PUT",
                            path,
                            json
                    );

            boolean success =
                    isSuccess(
                            response.statusCode
                    );

            if (!success) {

                LOG.error(
                        "Index document failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );
            }

            return success;

        } catch (Exception e) {

            LOG.error(
                    "Index document exception",
                    e
            );

            return false;
        }
    }

    /* =========================================================
       UPDATE DOCUMENT
       ========================================================= */

    public boolean updateDocument(
            String indexName,
            String documentId,
            String json) {

        if (!enabled
                || isBlank(indexName)
                || isBlank(documentId)
                || isBlank(json)) {

            return false;
        }

        try {

            String body =
                    "{\"doc\":" +
                            json +
                            "}";

            String path =
                    "/" +
                            encodePath(indexName) +
                            "/_update/" +
                            encodePath(documentId);

            HttpResponse response =
                    requestWithRetry(
                            "POST",
                            path,
                            body
                    );

            boolean success =
                    isSuccess(
                            response.statusCode
                    );

            if (!success) {

                LOG.error(
                        "Update document failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );
            }

            return success;

        } catch (Exception e) {

            LOG.error(
                    "Update document exception",
                    e
            );

            return false;
        }
    }

    /* =========================================================
       DELETE DOCUMENT
       ========================================================= */

    public boolean deleteDocument(
            String indexName,
            String documentId) {

        if (!enabled
                || isBlank(indexName)
                || isBlank(documentId)) {

            return false;
        }

        try {

            String path =
                    "/" +
                            encodePath(indexName) +
                            "/_doc/" +
                            encodePath(documentId);

            HttpResponse response =
                    requestWithRetry(
                            "DELETE",
                            path,
                            null
                    );

            boolean success =
                    response.statusCode == 200
                            || response.statusCode == 202
                            || response.statusCode == 404;

            if (!success) {

                LOG.error(
                        "Delete document failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );
            }

            return success;

        } catch (Exception e) {

            LOG.error(
                    "Delete document exception",
                    e
            );

            return false;
        }
    }

    /* =========================================================
       BULK INDEXING
       ========================================================= */

    public boolean bulk(
            String bulkBody) {

        if (!enabled
                || isBlank(bulkBody)) {

            return false;
        }

        try {

            HttpResponse response =
                    requestWithRetry(
                            "POST",
                            BULK_PATH,
                            bulkBody
                    );

            if (!isSuccess(response.statusCode)) {

                LOG.error(
                        "Bulk request failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );

                return false;
            }

            if (response.body != null
                    && response.body.contains(
                            "\"errors\":true"
            )) {

                LOG.error(
                        "Bulk request contains item failures: {}",
                        response.body
                );

                return false;
            }

            return true;

        } catch (Exception e) {

            LOG.error(
                    "Bulk indexing exception",
                    e
            );

            return false;
        }
    }

    /* =========================================================
       BASIC SEARCH
       ========================================================= */

    public String search(
            String query,
            int from,
            int size) {

        if (!enabled) {

            return "{\"error\":\"elasticsearch_disabled\"}";
        }

        if (isBlank(query)) {

            return "{\"error\":\"search_query_empty\"}";
        }

        if (from < 0) {

            from = 0;
        }

        if (size <= 0) {

            size = DEFAULT_PAGE_SIZE;
        }

        if (size > MAX_SEARCH_SIZE) {

            size = MAX_SEARCH_SIZE;
        }

        String safeQuery =
                escapeJson(
                        query.trim()
                );

        String body =
                "{"
                        + "\"from\":" + from + ","
                        + "\"size\":" + size + ","
                        + "\"track_total_hits\":true,"

                        + "\"query\":{"
                        + "\"bool\":{"
                        + "\"should\":["

                        + "{\"match\":{"
                        + "\"title\":{"
                        + "\"query\":\"" +
                        safeQuery +
                        "\","
                        + "\"boost\":3"
                        + "}"
                        + "}}"

                        + ",{\"match\":{"
                        + "\"description\":{"
                        + "\"query\":\"" +
                        safeQuery +
                        "\","
                        + "\"boost\":2"
                        + "}"
                        + "}}"

                        + ",{\"match\":{"
                        + "\"content\":{"
                        + "\"query\":\"" +
                        safeQuery +
                        "\","
                        + "\"boost\":1"
                        + "}"
                        + "}}"

                        + "],"
                        + "\"minimum_should_match\":1"
                        + "}"
                        + "},"

                        + "\"highlight\":{"
                        + "\"fields\":{"
                        + "\"title\":{},"
                        + "\"description\":{},"
                        + "\"content\":{}"
                        + "}"
                        + "},"

                        + "\"sort\":["
                        + "{\"_score\":{\"order\":\"desc\"}}"
                        + "]"

                        + "}";

        try {

            HttpResponse response =
                    requestWithRetry(
                            "POST",
                            "/" +
                                    encodePath(indexAlias) +
                                    "/_search",
                            body
                    );

            if (!isSuccess(response.statusCode)) {

                LOG.error(
                        "Search failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );

                return "{"
                        + "\"error\":\"search_http_error\","
                        + "\"status\":" +
                        response.statusCode +
                        ","
                        + "\"body\":\"" +
                        escapeJson(response.body) +
                        "\""
                        + "}";
            }

            return response.body;

        } catch (Exception e) {

            LOG.error(
                    "Search exception",
                    e
            );

            return "{"
                    + "\"error\":\"search_exception\","
                    + "\"message\":\"" +
                    escapeJson(e.getMessage()) +
                    "\""
                    + "}";
        }
    }

    /* =========================================================
       FILTERED SEARCH - BACKWARD COMPATIBILITY
       ========================================================= */

    public String search(
            String query,
            String template,
            String contentType,
            String tag,
            int page,
            int size) {

        return search(
                query,
                template,
                contentType,
                tag,
                "relevance",
                page,
                size
        );
    }

    /* =========================================================
       FILTERED SEARCH + FACETS + SORTING
       ========================================================= */

    public String search(
            String query,
            String template,
            String contentType,
            String tag,
            String sort,
            int page,
            int size) {

        if (!enabled) {

            return "{\"error\":\"elasticsearch_disabled\"}";
        }

        if (page <= 0) {

            page = 1;
        }

        if (size <= 0) {

            size = DEFAULT_PAGE_SIZE;
        }

        if (size > MAX_SEARCH_SIZE) {

            size = MAX_SEARCH_SIZE;
        }

        /*
         * Default sorting
         */
        if (isBlank(sort)) {

            sort = "relevance";
        }

        /*
         * Only supported sorting values are accepted.
         */
        if (!"relevance".equalsIgnoreCase(sort)
                && !"lastModified".equalsIgnoreCase(sort)
                && !"title".equalsIgnoreCase(sort)) {

            sort = "relevance";
        }

        int from =
                (page - 1) * size;

        StringBuilder body =
                new StringBuilder();

        body.append("{");

        body.append("\"from\":")
                .append(from)
                .append(",");

        body.append("\"size\":")
                .append(size)
                .append(",");

        body.append("\"track_total_hits\":true,");

        /* =====================================================
           QUERY
           ===================================================== */

        body.append("\"query\":{");
        body.append("\"bool\":{");

        if (!isBlank(query)) {

            String safeQuery =
                    escapeJson(
                            query.trim()
                    );

            body.append("\"must\":[");
            body.append("{\"bool\":{\"should\":[");

            body.append(
                    "{\"match\":{\"title\":{" +
                            "\"query\":\"" +
                            safeQuery +
                            "\"," +
                            "\"boost\":3" +
                            "}}},"
            );

            body.append(
                    "{\"match\":{\"description\":{" +
                            "\"query\":\"" +
                            safeQuery +
                            "\"," +
                            "\"boost\":2" +
                            "}}},"
            );

            body.append(
                    "{\"match\":{\"content\":{" +
                            "\"query\":\"" +
                            safeQuery +
                            "\"," +
                            "\"boost\":1" +
                            "}}}"
            );

            body.append(
                    "],\"minimum_should_match\":1}}]"
            );

        } else {

            body.append(
                    "\"must\":[{\"match_all\":{}}]"
            );
        }

        /* =====================================================
           FILTERS
           ===================================================== */

        boolean hasFilter =
                !isBlank(template)
                        || !isBlank(contentType)
                        || !isBlank(tag);

        if (hasFilter) {

            body.append(",\"filter\":[");

            boolean first = true;

            /*
             * Template filter
             */
            if (!isBlank(template)) {

                body.append(
                        "{\"term\":{\"template\":\"" +
                                escapeJson(
                                        template.trim()
                                ) +
                                "\"}}"
                );

                first = false;
            }

            /*
             * Content Type filter
             */
            if (!isBlank(contentType)) {

                if (!first) {

                    body.append(",");
                }

                body.append(
                        "{\"term\":{\"contentType\":\"" +
                                escapeJson(
                                        contentType.trim()
                                ) +
                                "\"}}"
                );

                first = false;
            }

            /*
             * Tag filter
             */
            if (!isBlank(tag)) {

                if (!first) {

                    body.append(",");
                }

                body.append(
                        "{\"term\":{\"tags\":\"" +
                                escapeJson(
                                        tag.trim()
                                ) +
                                "\"}}"
                );
            }

            body.append("]");
        }

        body.append("}");
        body.append("},");

        /* =====================================================
           HIGHLIGHT
           ===================================================== */

        body.append("\"highlight\":{");
        body.append("\"fields\":{");
        body.append("\"title\":{},");
        body.append("\"description\":{},");
        body.append("\"content\":{}");
        body.append("}");
        body.append("},");

        /* =====================================================
           SORTING
           ===================================================== */

        body.append("\"sort\":[");

        if ("lastModified".equalsIgnoreCase(sort)) {

            /*
             * Latest modified first.
             */
            body.append(
                    "{\"lastModified\":{" +
                            "\"order\":\"desc\"," +
                            "\"missing\":\"_last\"" +
                            "}},"
            );

            /*
             * Score as secondary sort.
             */
            body.append(
                    "{\"_score\":{\"order\":\"desc\"}}"
            );

        } else if ("title".equalsIgnoreCase(sort)) {

            /*
             * Alphabetical title sorting.
             * title.raw is keyword field.
             */
            body.append(
                    "{\"title.raw\":{" +
                            "\"order\":\"asc\"," +
                            "\"missing\":\"_last\"" +
                            "}},"
            );

            /*
             * Score as secondary sort.
             */
            body.append(
                    "{\"_score\":{\"order\":\"desc\"}}"
            );

        } else {

            /*
             * Relevance sorting.
             * Highest score first.
             */
            body.append(
                    "{\"_score\":{\"order\":\"desc\"}},"
            );

            /*
             * Last modified as secondary sort.
             */
            body.append(
                    "{\"lastModified\":{" +
                            "\"order\":\"desc\"," +
                            "\"missing\":\"_last\"" +
                            "}}"
            );
        }

        body.append("],");

        /* =====================================================
           FACETS / AGGREGATIONS
           ===================================================== */

        body.append("\"aggs\":{");

        /*
         * TAGS FACET
         */
        body.append("\"tags\":{");
        body.append("\"terms\":{");
        body.append("\"field\":\"tags\",");
        body.append("\"size\":20");
        body.append("}");
        body.append("},");

        /*
         * TEMPLATE FACET
         */
        body.append("\"templates\":{");
        body.append("\"terms\":{");
        body.append("\"field\":\"template\",");
        body.append("\"size\":20");
        body.append("}");
        body.append("},");

        /*
         * CONTENT TYPE FACET
         */
        body.append("\"contentTypes\":{");
        body.append("\"terms\":{");
        body.append("\"field\":\"contentType\",");
        body.append("\"size\":20");
        body.append("}");
        body.append("}");

        body.append("}");

        /*
         * Close root JSON object.
         */
        body.append("}");

        /* =====================================================
           EXECUTE SEARCH
           ===================================================== */

        try {

            HttpResponse response =
                    requestWithRetry(
                            "POST",
                            "/" +
                                    encodePath(indexAlias) +
                                    "/_search",
                            body.toString()
                    );

            if (!isSuccess(response.statusCode)) {

                LOG.error(
                        "Filtered search failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );

                return "{"
                        + "\"error\":\"search_http_error\","
                        + "\"status\":" +
                        response.statusCode +
                        ","
                        + "\"body\":\"" +
                        escapeJson(response.body) +
                        "\""
                        + "}";
            }

            return response.body;

        } catch (Exception e) {

            LOG.error(
                    "Filtered search exception",
                    e
            );

            return "{"
                    + "\"error\":\"search_failed\","
                    + "\"message\":\"" +
                    escapeJson(e.getMessage()) +
                    "\""
                    + "}";
        }
    }

    /* =========================================================
       AUTOCOMPLETE
       ========================================================= */

    public String autocomplete(
            String query,
            int size) {

        if (!enabled) {

            return "{\"error\":\"elasticsearch_disabled\"}";
        }

        if (isBlank(query)) {

            return "{\"error\":\"autocomplete_query_empty\"}";
        }

        if (size <= 0) {

            size = DEFAULT_PAGE_SIZE;
        }

        if (size > MAX_AUTOCOMPLETE_SIZE) {

            size = MAX_AUTOCOMPLETE_SIZE;
        }

        String body =
                "{"
                        + "\"size\":" +
                        size +
                        ","

                        + "\"_source\":["
                        + "\"id\","
                        + "\"path\","
                        + "\"title\""
                        + "],"

                        + "\"query\":{"
                        + "\"match_bool_prefix\":{"
                        + "\"title.autocomplete\":\"" +
                        escapeJson(
                                query.trim()
                        ) +
                        "\""
                        + "}"
                        + "},"

                        + "\"sort\":["
                        + "{\"_score\":{\"order\":\"desc\"}}"
                        + "]"

                        + "}";

        try {

            HttpResponse response =
                    requestWithRetry(
                            "POST",
                            "/" +
                                    encodePath(indexAlias) +
                                    "/_search",
                            body
                    );

            if (!isSuccess(response.statusCode)) {

                LOG.error(
                        "Autocomplete failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );

                return "{"
                        + "\"error\":\"autocomplete_http_error\","
                        + "\"status\":" +
                        response.statusCode +
                        ","
                        + "\"body\":\"" +
                        escapeJson(response.body) +
                        "\""
                        + "}";
            }

            return response.body;

        } catch (Exception e) {

            LOG.error(
                    "Autocomplete exception",
                    e
            );

            return "{"
                    + "\"error\":\"autocomplete_failed\","
                    + "\"message\":\"" +
                    escapeJson(e.getMessage()) +
                    "\""
                    + "}";
        }
    }

    /* =========================================================
       ALIAS SWITCH
       ========================================================= */

    public boolean switchAlias(
            String alias,
            String newIndex) {

        if (!enabled
                || isBlank(alias)
                || isBlank(newIndex)) {

            return false;
        }

        try {

            String currentIndex =
                    extractCurrentAliasIndex(
                            getAlias(alias)
                    );

            StringBuilder actions =
                    new StringBuilder();

            actions.append(
                    "{\"actions\":["
            );

            if (!isBlank(currentIndex)
                    && !currentIndex.equals(newIndex)) {

                actions.append(
                        "{\"remove\":{" +
                                "\"index\":\"" +
                                escapeJson(currentIndex) +
                                "\"," +
                                "\"alias\":\"" +
                                escapeJson(alias) +
                                "\"}}"
                );

                actions.append(",");
            }

            actions.append(
                    "{\"add\":{" +
                            "\"index\":\"" +
                            escapeJson(newIndex) +
                            "\"," +
                            "\"alias\":\"" +
                            escapeJson(alias) +
                            "\"}}"
            );

            actions.append("]}");

            HttpResponse response =
                    requestWithRetry(
                            "POST",
                            ALIASES_PATH,
                            actions.toString()
                    );

            boolean success =
                    isSuccess(
                            response.statusCode
                    );

            if (success) {

                LOG.info(
                        "Alias switched. alias={}, index={}",
                        alias,
                        newIndex
                );

            } else {

                LOG.error(
                        "Alias switch failed. status={}, body={}",
                        response.statusCode,
                        response.body
                );
            }

            return success;

        } catch (Exception e) {

            LOG.error(
                    "Alias switch exception",
                    e
            );

            return false;
        }
    }

    /* =========================================================
       GET ALIAS
       ========================================================= */

    public String getAlias() {

        return getAlias(indexAlias);
    }

    public String getAlias(
            String alias) {

        if (isBlank(alias)) {

            return "{}";
        }

        try {

            HttpResponse response =
                    requestWithRetry(
                            "GET",
                            "/_alias/" +
                                    encodePath(alias),
                            null
                    );

            if (response.statusCode == 200) {

                return response.body;
            }

            return "{}";

        } catch (Exception e) {

            LOG.error(
                    "Get alias exception: {}",
                    alias,
                    e
            );

            return "{}";
        }
    }

    private String extractCurrentAliasIndex(
            String aliasResponse) {

        if (isBlank(aliasResponse)
                || "{}".equals(
                        aliasResponse.trim()
                )) {

            return "";
        }

        int aliasesPosition =
                aliasResponse.indexOf(
                        "\"aliases\""
                );

        if (aliasesPosition <= 0) {

            return "";
        }

        String beforeAliases =
                aliasResponse.substring(
                        0,
                        aliasesPosition
                );

        int lastQuote =
                beforeAliases.lastIndexOf(
                        "\""
                );

        if (lastQuote <= 0) {

            return "";
        }

        int previousQuote =
                beforeAliases.lastIndexOf(
                        "\"",
                        lastQuote - 1
                );

        if (previousQuote < 0) {

            return "";
        }

        return beforeAliases.substring(
                previousQuote + 1,
                lastQuote
        );
    }

    /* =========================================================
       RETRY
       ========================================================= */

    private HttpResponse requestWithRetry(
            String method,
            String path,
            String body) throws Exception {

        Exception lastException = null;

        for (int attempt = 1;
             attempt <= MAX_RETRIES;
             attempt++) {

            /*
             * Structured attempt log.
             */
            LOG.info(
                    "event=es_request_attempt attempt={}/{} method={} path={}",
                    attempt,
                    MAX_RETRIES,
                    method,
                    path
            );

            try {

                HttpResponse response =
                        request(
                                method,
                                path,
                                body
                        );

                /*
                 * Structured response log.
                 */
                LOG.info(
                        "event=es_request_response attempt={}/{} method={} path={} status={}",
                        attempt,
                        MAX_RETRIES,
                        method,
                        path,
                        response.statusCode
                );

                /*
                 * Retry only transient HTTP status codes.
                 */
                if (isRetriable(response.statusCode)) {

                    if (attempt < MAX_RETRIES) {

                        /*
                         * Exponential backoff:
                         *
                         * attempt 1 -> 500 ms
                         * attempt 2 -> 1000 ms
                         *
                         * Formula:
                         * 500 * 2^(attempt - 1)
                         */
                        long delay =
                                RETRY_DELAY *
                                        (1L << (attempt - 1));

                        LOG.warn(
                                "event=es_retry attempt={}/{} status={} delayMs={} method={} path={}",
                                attempt,
                                MAX_RETRIES,
                                response.statusCode,
                                delay,
                                method,
                                path
                        );

                        Thread.sleep(delay);

                        continue;
                    }

                    /*
                     * Final transient HTTP failure.
                     */
                    LOG.error(
                            "event=es_retry_exhausted attempts={} method={} path={} status={}",
                            MAX_RETRIES,
                            method,
                            path,
                            response.statusCode
                    );

                    return response;
                }

                /*
                 * Successful or non-retriable response.
                 */
                return response;

            } catch (Exception e) {

                lastException = e;

                /*
                 * Current attempt failed because of
                 * connection / IO / request exception.
                 */
                LOG.warn(
                        "event=es_request_exception attempt={}/{} method={} path={} message={}",
                        attempt,
                        MAX_RETRIES,
                        method,
                        path,
                        e.getMessage()
                );

                /*
                 * Retry exception when attempts remain.
                 */
                if (attempt < MAX_RETRIES) {

                    long delay =
                            RETRY_DELAY *
                                    (1L << (attempt - 1));

                    LOG.warn(
                            "event=es_retry_exception attempt={}/{} delayMs={} method={} path={}",
                            attempt,
                            MAX_RETRIES,
                            delay,
                            method,
                            path
                    );

                    Thread.sleep(delay);

                } else {

                    /*
                     * Final exception after all attempts.
                     */
                    LOG.error(
                            "event=es_retry_exhausted attempts={} method={} path={} message={}",
                            MAX_RETRIES,
                            method,
                            path,
                            e.getMessage(),
                            e
                    );
                }
            }
        }

        if (lastException != null) {

            throw lastException;
        }

        throw new IllegalStateException(
                "Elasticsearch request failed after retries"
        );
    }

    private boolean isRetriable(
            int statusCode) {

        return statusCode == 429
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    /* =========================================================
       HTTP REQUEST
       ========================================================= */

    private HttpResponse request(
            String method,
            String path,
            String body) throws Exception {

        if (isBlank(serverUrl)) {

            throw new IllegalStateException(
                    "Elasticsearch server URL is empty"
            );
        }

        URL url =
                new URL(
                        serverUrl + path
                );

        HttpURLConnection connection =
                null;

        try {

            connection =
                    openConnection(url);

            connection.setRequestMethod(
                    method
            );

            connection.setConnectTimeout(
                    connectTimeout
            );

            connection.setReadTimeout(
                    readTimeout
            );

            connection.setUseCaches(false);
            connection.setDoInput(true);

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            connection.setRequestProperty(
                    "Accept-Encoding",
                    "identity"
            );

            addAuthentication(
                    connection
            );

            if (body != null) {

                connection.setDoOutput(true);

                if (BULK_PATH.equals(path)) {

                    connection.setRequestProperty(
                            "Content-Type",
                            "application/x-ndjson"
                    );

                } else {

                    connection.setRequestProperty(
                            "Content-Type",
                            "application/json"
                    );
                }

                byte[] payload =
                        body.getBytes(
                                StandardCharsets.UTF_8
                        );

                connection.setFixedLengthStreamingMode(
                        payload.length
                );

                try (
                        OutputStream out =
                                connection.getOutputStream()
                ) {

                    out.write(payload);
                    out.flush();
                }
            }

            int statusCode =
                    connection.getResponseCode();

            InputStream inputStream =
                    statusCode >= 400
                            ? connection.getErrorStream()
                            : connection.getInputStream();

            String responseBody =
                    readResponse(
                            inputStream
                    );

            return new HttpResponse(
                    statusCode,
                    responseBody
            );

        } finally {

            if (connection != null) {

                connection.disconnect();
            }
        }
    }

    /* =========================================================
       OPEN CONNECTION
       ========================================================= */

    private HttpURLConnection openConnection(
            URL url) throws Exception {

        HttpURLConnection connection =
                (HttpURLConnection)
                        url.openConnection();

        if (connection instanceof HttpsURLConnection
                && trustAllSsl) {

            HttpsURLConnection https =
                    (HttpsURLConnection)
                            connection;

            https.setSSLSocketFactory(
                    createDevSslSocketFactory()
            );

            https.setHostnameVerifier(
                    createDevHostnameVerifier()
            );
        }

        return connection;
    }

    /* =========================================================
       LOCAL SSL
       ========================================================= */

    private SSLSocketFactory
    createDevSslSocketFactory() throws Exception {

        TrustManager[] trustManagers =
                new TrustManager[]{
                        new X509TrustManager() {

                            @Override
                            public X509Certificate[]
                            getAcceptedIssuers() {

                                return new X509Certificate[0];
                            }

                            @Override
                            public void checkClientTrusted(
                                    X509Certificate[] certs,
                                    String authType) {
                            }

                            @Override
                            public void checkServerTrusted(
                                    X509Certificate[] certs,
                                    String authType) {
                            }
                        }
                };

        SSLContext context =
                SSLContext.getInstance("TLS");

        context.init(
                null,
                trustManagers,
                new SecureRandom()
        );

        return context.getSocketFactory();
    }

    private HostnameVerifier
    createDevHostnameVerifier() {

        return (
                hostname,
                session
        ) -> true;
    }

    /* =========================================================
       BASIC AUTH
       ========================================================= */

    private void addAuthentication(
            HttpURLConnection connection) {

        if (isBlank(username)) {

            return;
        }

        String credentials =
                username +
                        ":" +
                        (password == null
                                ? ""
                                : password);

        String encoded =
                Base64.getEncoder()
                        .encodeToString(
                                credentials.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );

        connection.setRequestProperty(
                "Authorization",
                "Basic " + encoded
        );
    }

    /* =========================================================
       READ RESPONSE
       ========================================================= */

    private String readResponse(
            InputStream inputStream) throws Exception {

        if (inputStream == null) {

            return "";
        }

        StringBuilder result =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        inputStream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                result.append(line);
            }
        }

        return result.toString();
    }

    /* =========================================================
       JSON ESCAPE
       ========================================================= */

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

    /* =========================================================
       PATH ENCODE
       ========================================================= */

    private String encodePath(
            String value) {

        if (value == null) {

            return "";
        }

        return value
                .replace("%", "%25")
                .replace(" ", "%20")
                .replace("/", "%2F")
                .replace("?", "%3F")
                .replace("#", "%23")
                .replace("\"", "%22")
                .replace("<", "%3C")
                .replace(">", "%3E");
    }

    /* =========================================================
       HELPERS
       ========================================================= */

    private String removeTrailingSlash(
            String value) {

        if (value == null) {

            return "";
        }

        while (value.endsWith("/")) {

            value =
                    value.substring(
                            0,
                            value.length() - 1
                    );
        }

        return value;
    }

    private boolean isSuccess(
            int statusCode) {

        return statusCode >= 200
                && statusCode < 300;
    }

    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }

    /* =========================================================
       RESPONSE HOLDER
       ========================================================= */

    private static class HttpResponse {

        private final int statusCode;
        private final String body;

        private HttpResponse(
                int statusCode,
                String body) {

            this.statusCode = statusCode;
            this.body = body;
        }
    }

    /* =========================================================
       OSGI CONFIG
       ========================================================= */

    @ObjectClassDefinition(
            name = "Workspace Elasticsearch Service"
    )
    public @interface Config {

        @AttributeDefinition(
                name = "Enabled"
        )
        boolean enabled() default true;

        @AttributeDefinition(
                name = "Elasticsearch Server URL"
        )
        String serverUrl()
                default "https://localhost:9200";

        @AttributeDefinition(
                name = "Username"
        )
        String username()
                default "elastic";

        @AttributeDefinition(
                name = "Password",
                description =
                        "Configure through environment-specific OSGi configuration"
        )
        String password()
                default "";

        @AttributeDefinition(
                name = "Index Alias"
        )
        String indexAlias()
                default "workspace-content";

        @AttributeDefinition(
                name = "Index Pattern"
        )
        String indexPattern()
                default "workspace-content-*";

        @AttributeDefinition(
                name = "Index Template Name"
        )
        String indexTemplateName()
                default "workspace-content-template";

        @AttributeDefinition(
                name = "Number Of Shards"
        )
        int numberOfShards()
                default 1;

        @AttributeDefinition(
                name = "Number Of Replicas"
        )
        int numberOfReplicas()
                default 0;

        @AttributeDefinition(
                name = "Connection Timeout"
        )
        int connectTimeout()
                default 5000;

        @AttributeDefinition(
                name = "Read Timeout"
        )
        int readTimeout()
                default 10000;

        @AttributeDefinition(
                name = "Trust All SSL",
                description =
                        "Use only for local self-signed Elasticsearch. " +
                                "Disable in higher environments."
        )
        boolean trustAllSsl()
                default true;
    }
}