package com.workspace.core.servlets;

import com.workspace.core.services.ElasticsearchService;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/workspace/elasticsearch/full-index",
                "sling.servlet.methods=POST"
        }
)
public class ElasticsearchFullIndexServlet
        extends org.apache.sling.api.servlets.SlingAllMethodsServlet {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ElasticsearchFullIndexServlet.class
            );

    private static final String CONTENT_ROOT =
            "/content/workspace";

    private static final int BULK_BATCH_SIZE =
            100;

    @Reference
    private ElasticsearchService elasticsearchService;

    /* =========================================================
       FULL INDEX
       ========================================================= */

    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        LOG.info(
                "Elasticsearch full indexing started. Content root: {}",
                CONTENT_ROOT
        );

        ResourceResolver resourceResolver =
                request.getResourceResolver();

        /* -----------------------------------------------------
           1. Check AEM content root
           ----------------------------------------------------- */

        Resource rootResource =
                resourceResolver.getResource(
                        CONTENT_ROOT
                );

        if (rootResource == null) {

            LOG.error(
                    "Content root not found: {}",
                    CONTENT_ROOT
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_NOT_FOUND,
                    "Content root not found",
                    CONTENT_ROOT
            );

            return;
        }

        /* -----------------------------------------------------
           2. Check Elasticsearch service
           ----------------------------------------------------- */

        if (!elasticsearchService.isEnabled()) {

            LOG.error(
                    "Elasticsearch service is disabled"
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Elasticsearch service is disabled",
                    null
            );

            return;
        }

        /* -----------------------------------------------------
           3. Check Elasticsearch connectivity
           ----------------------------------------------------- */

        if (!elasticsearchService.isHealthy()) {

            LOG.error(
                    "Elasticsearch is not reachable"
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Elasticsearch is not reachable",
                    null
            );

            return;
        }

        /* -----------------------------------------------------
           4. Create new versioned index
           ----------------------------------------------------- */

        String newIndex =
                elasticsearchService.createVersionedIndex();

        if (isBlank(newIndex)) {

            LOG.error(
                    "Unable to create versioned Elasticsearch index"
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to create versioned Elasticsearch index",
                    null
            );

            return;
        }

        LOG.info(
                "New versioned Elasticsearch index created: {}",
                newIndex
        );

        /* -----------------------------------------------------
           5. Get PageManager
           ----------------------------------------------------- */

        PageManager pageManager =
                resourceResolver.adaptTo(
                        PageManager.class
                );

        if (pageManager == null) {

            LOG.error(
                    "Unable to obtain PageManager"
            );

            elasticsearchService.deleteIndex(
                    newIndex
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to obtain PageManager",
                    null
            );

            return;
        }

        /* -----------------------------------------------------
           6. Get root page
           ----------------------------------------------------- */

        Page rootPage =
                pageManager.getPage(
                        CONTENT_ROOT
                );

        if (rootPage == null) {

            LOG.error(
                    "Root page not found: {}",
                    CONTENT_ROOT
            );

            elasticsearchService.deleteIndex(
                    newIndex
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_NOT_FOUND,
                    "AEM root page not found",
                    CONTENT_ROOT
            );

            return;
        }

        /* -----------------------------------------------------
           7. Collect all child pages
           ----------------------------------------------------- */

        List<Page> pages =
                new ArrayList<>();

        collectPages(
                rootPage,
                pages
        );

        LOG.info(
                "Pages found for full indexing: {}",
                pages.size()
        );

        /* -----------------------------------------------------
           8. Bulk index pages
           ----------------------------------------------------- */

        int indexedCount = 0;
        int failedCount = 0;

        int batchCount = 0;

        StringBuilder bulkBody =
                new StringBuilder();

        for (Page page : pages) {

            if (page == null) {
                continue;
            }

            try {

                String document =
                        createDocument(
                                page
                        );

                if (isBlank(document)) {

                    failedCount++;

                    LOG.warn(
                            "Skipping page because generated document is empty: {}",
                            page.getPath()
                    );

                    continue;
                }

                /*
                 * Stable Elasticsearch document ID:
                 * AEM page path.
                 *
                 * This makes indexing idempotent.
                 */
                bulkBody.append(
                        "{\"index\":{"
                                + "\"_index\":\""
                                + escapeJson(newIndex)
                                + "\","
                                + "\"_id\":\""
                                + escapeJson(page.getPath())
                                + "\"}}\n"
                );

                bulkBody.append(
                        document
                );

                bulkBody.append(
                        "\n"
                );

                batchCount++;

                if (batchCount >= BULK_BATCH_SIZE) {

                    boolean success =
                            sendBulk(
                                    bulkBody.toString()
                            );

                    if (success) {

                        indexedCount +=
                                batchCount;

                    } else {

                        failedCount +=
                                batchCount;
                    }

                    bulkBody.setLength(0);
                    batchCount = 0;
                }

            } catch (Exception e) {

                failedCount++;

                LOG.error(
                        "Failed to prepare page for indexing: {}",
                        page.getPath(),
                        e
                );
            }
        }

        /* -----------------------------------------------------
           9. Send remaining batch
           ----------------------------------------------------- */

        if (batchCount > 0
                && bulkBody.length() > 0) {

            boolean success =
                    sendBulk(
                            bulkBody.toString()
                    );

            if (success) {

                indexedCount +=
                        batchCount;

            } else {

                failedCount +=
                        batchCount;
            }
        }

        LOG.info(
                "Bulk indexing finished. Pages: {}, Indexed: {}, Failed: {}",
                pages.size(),
                indexedCount,
                failedCount
        );

        /* -----------------------------------------------------
           10. Never switch alias if indexing failed
           ----------------------------------------------------- */

        if (failedCount > 0) {

            LOG.error(
                    "Full indexing failed. Alias will not be switched. Index: {}",
                    newIndex
            );

            /*
             * Remove failed temporary index so the next rebuild
             * starts cleanly.
             */
            elasticsearchService.deleteIndex(
                    newIndex
            );

            response.setStatus(
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().write(
                    "{"
                            + "\"status\":\"ERROR\","
                            + "\"message\":\"Full indexing failed. Alias was not switched.\","
                            + "\"pages\":"
                            + pages.size()
                            + ","
                            + "\"indexed\":"
                            + indexedCount
                            + ","
                            + "\"failed\":"
                            + failedCount
                            + "}"
            );

            return;
        }

        /* -----------------------------------------------------
           11. Get configured stable alias
           ----------------------------------------------------- */

        String indexAlias =
                elasticsearchService.getIndexAlias();

        if (isBlank(indexAlias)) {

            LOG.error(
                    "Configured Elasticsearch index alias is empty"
            );

            elasticsearchService.deleteIndex(
                    newIndex
            );

            writeError(
                    response,
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Elasticsearch index alias is empty",
                    null
            );

            return;
        }

        /* -----------------------------------------------------
           12. Atomic alias cutover
           ----------------------------------------------------- */

        boolean aliasSwitched =
                elasticsearchService.switchAlias(
                        indexAlias,
                        newIndex
                );

        if (!aliasSwitched) {

            LOG.error(
                    "Alias switch failed. Index: {}, Alias: {}",
                    newIndex,
                    indexAlias
            );

            /*
             * Index contains a complete dataset, but it was not
             * switched to the live alias. Remove it to keep
             * failed rebuilds from accumulating.
             */
            elasticsearchService.deleteIndex(
                    newIndex
            );

            response.setStatus(
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().write(
                    "{"
                            + "\"status\":\"ERROR\","
                            + "\"message\":\"Indexing completed but alias switch failed\","
                            + "\"alias\":\""
                            + escapeJson(indexAlias)
                            + "\","
                            + "\"pages\":"
                            + pages.size()
                            + ","
                            + "\"indexed\":"
                            + indexedCount
                            + "}"
            );

            return;
        }

        /* -----------------------------------------------------
           13. Success
           ----------------------------------------------------- */

        LOG.info(
                "Elasticsearch full indexing completed successfully. " +
                        "Index: {}, Alias: {}, Documents: {}",
                newIndex,
                indexAlias,
                indexedCount
        );

        response.setStatus(
                SlingHttpServletResponse.SC_OK
        );

        response.getWriter().write(
                "{"
                        + "\"status\":\"SUCCESS\","
                        + "\"message\":\"Full indexing completed successfully\","
                        + "\"index\":\""
                        + escapeJson(newIndex)
                        + "\","
                        + "\"alias\":\""
                        + escapeJson(indexAlias)
                        + "\","
                        + "\"pages\":"
                        + pages.size()
                        + ","
                        + "\"indexed\":"
                        + indexedCount
                        + ","
                        + "\"failed\":"
                        + failedCount
                        + "}"
        );
    }

    /* =========================================================
       SEND BULK
       ========================================================= */

    private boolean sendBulk(
            String bulkBody) {

        if (isBlank(bulkBody)) {
            return true;
        }

        LOG.info(
                "Sending Elasticsearch bulk request"
        );

        boolean success =
                elasticsearchService.bulk(
                        bulkBody
                );

        if (success) {

            LOG.info(
                    "Elasticsearch bulk request completed successfully"
            );

        } else {

            LOG.error(
                    "Elasticsearch bulk request failed"
            );
        }

        return success;
    }

    /* =========================================================
       COLLECT PAGES
       ========================================================= */

    private void collectPages(
            Page page,
            List<Page> pages) {

        if (page == null) {
            return;
        }

        /*
         * The /content/workspace root itself is treated as the
         * container root and its child pages are indexed.
         */
        if (!CONTENT_ROOT.equals(
                page.getPath()
        )) {

            pages.add(page);
        }

        Iterator<Page> children =
                page.listChildren();

        while (
                children.hasNext()
        ) {

            Page child =
                    children.next();

            collectPages(
                    child,
                    pages
            );
        }
    }

    /* =========================================================
       CREATE DOCUMENT
       ========================================================= */

    private String createDocument(
            Page page) {

        if (page == null) {
            return null;
        }

        ValueMap properties =
                page.getProperties();

        /* -----------------------------------------------------
           Title
           ----------------------------------------------------- */

        String title =
                getString(
                        properties,
                        "jcr:title"
                );

        if (isBlank(title)) {
            title = page.getTitle();
        }

        /* -----------------------------------------------------
           Description
           ----------------------------------------------------- */

        String description =
                getString(
                        properties,
                        "jcr:description"
                );

        /* -----------------------------------------------------
           Template
           ----------------------------------------------------- */

        String template =
                getString(
                        properties,
                        "cq:template"
                );

        /* -----------------------------------------------------
           Content Type
           ----------------------------------------------------- */

        String contentType =
                getString(
                        properties,
                        "sling:resourceType"
                );

        /* -----------------------------------------------------
           Tags
           ----------------------------------------------------- */

        String[] tags =
                properties.get(
                        "cq:tags",
                        String[].class
                );

        /* -----------------------------------------------------
           Page content
           ----------------------------------------------------- */

        String content =
                extractText(
                        page.getContentResource()
                );

        /* -----------------------------------------------------
           Last modified
           ----------------------------------------------------- */

        Calendar lastModified =
                properties.get(
                        "cq:lastModified",
                        Calendar.class
                );

        /* -----------------------------------------------------
           Build JSON
           ----------------------------------------------------- */

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        /*
         * Stable ID.
         */
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

        /*
         * AEM path.
         */
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

        /*
         * Title.
         */
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

        /*
         * Description.
         */
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

        /*
         * Full textual content.
         */
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

        /*
         * Template.
         */
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

        /*
         * Content type/resource type.
         */
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

        /*
         * Tags.
         */
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

                json.append(
                        "\""
                );

                json.append(
                        escapeJson(
                                tags[i]
                        )
                );

                json.append(
                        "\""
                );
            }
        }

        json.append(
                "]"
        );

        /*
         * Last modified is optional.
         */
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

            json.append(
                    "\""
            );
        }

        json.append(
                "}"
        );

        return json.toString();
    }

    /* =========================================================
       EXTRACT TEXT FROM PAGE CONTENT
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

            /*
             * For search indexing, only textual String
             * properties are collected.
             */
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

    /* =========================================================
       GET STRING PROPERTY
       ========================================================= */

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

    /* =========================================================
       ERROR RESPONSE
       ========================================================= */

    private void writeError(
            SlingHttpServletResponse response,
            int status,
            String message,
            String path)
            throws IOException {

        response.setStatus(status);

        StringBuilder json =
                new StringBuilder();

        json.append("{");
        json.append("\"status\":\"ERROR\",");
        json.append("\"message\":\"");
        json.append(
                escapeJson(
                        message
                )
        );
        json.append("\"");

        if (!isBlank(path)) {

            json.append(
                    ",\"path\":\""
            );

            json.append(
                    escapeJson(
                            path
                    )
            );

            json.append(
                    "\""
            );
        }

        json.append("}");

        response.getWriter().write(
                json.toString()
        );
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
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\t",
                        "\\t"
                )
                .replace(
                        "\b",
                        "\\b"
                )
                .replace(
                        "\f",
                        "\\f"
                );
    }

    /* =========================================================
       BLANK CHECK
       ========================================================= */

    private boolean isBlank(
            String value) {

        return value == null
                || value.trim().isEmpty();
    }
}