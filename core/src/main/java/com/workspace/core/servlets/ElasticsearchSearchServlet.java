package com.workspace.core.servlets;

import com.workspace.core.services.ElasticsearchService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.ServletException;
import java.io.IOException;

@Component(
        service = javax.servlet.Servlet.class,
        property = {
                "sling.servlet.paths=/bin/workspace/elasticsearch/search-mvp",
                "sling.servlet.methods=GET",
                "service.ranking:Integer=10000"
        }
)
public class ElasticsearchSearchServlet
        extends SlingSafeMethodsServlet {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 20;

    private static final String DEFAULT_SORT = "relevance";

    @Reference
    private ElasticsearchService elasticsearchService;

    @Override
    protected void doGet(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        /*
         * Search keyword
         *
         * Example:
         * ?q=elasticsearch
         */
        String query = request.getParameter("q");

        /*
         * Optional filters
         */
        String template = request.getParameter("template");
        String contentType = request.getParameter("contentType");
        String tag = request.getParameter("tag");

        /*
         * Optional sorting
         *
         * Allowed:
         * relevance
         * lastModified
         * title
         */
        String sort = request.getParameter("sort");

        if (sort == null || sort.trim().isEmpty()) {
            sort = DEFAULT_SORT;
        }

        sort = sort.trim();

        /*
         * Validate sort value
         */
        if (!isValidSort(sort)) {

            response.setStatus(
                    SlingHttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().write(
                    "{"
                    + "\"servlet\":\"SEARCH_MVP_SERVLET_HIT\","
                    + "\"error\":\"Invalid sort value. Allowed values are relevance, lastModified, title\""
                    + "}"
            );

            return;
        }

        /*
         * q is still required for current MVP search.
         */
        if (query == null || query.trim().isEmpty()) {

            response.setStatus(
                    SlingHttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().write(
                    "{"
                    + "\"servlet\":\"SEARCH_MVP_SERVLET_HIT\","
                    + "\"error\":\"Search query 'q' is required\""
                    + "}"
            );

            return;
        }

        /*
         * Pagination
         */
        int page = DEFAULT_PAGE;
        int size = DEFAULT_SIZE;

        try {

            String pageParam =
                    request.getParameter("page");

            String sizeParam =
                    request.getParameter("size");

            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }

            if (sizeParam != null) {
                size = Integer.parseInt(sizeParam);
            }

        } catch (NumberFormatException e) {

            response.setStatus(
                    SlingHttpServletResponse.SC_BAD_REQUEST
            );

            response.getWriter().write(
                    "{"
                    + "\"servlet\":\"SEARCH_MVP_SERVLET_HIT\","
                    + "\"error\":\"page and size must be valid numbers\""
                    + "}"
            );

            return;
        }

        if (page < 1) {
            page = 1;
        }

        if (size < 1) {
            size = DEFAULT_SIZE;
        }

        if (size > MAX_SIZE) {
            size = MAX_SIZE;
        }

        /*
         * Execute Elasticsearch search
         *
         * Sorting is passed to service.
         */
        String result =
                elasticsearchService.search(
                        query.trim(),
                        template,
                        contentType,
                        tag,
                        sort,
                        page,
                        size
                );

        /*
         * Empty Elasticsearch response
         */
        if (result == null || result.trim().isEmpty()) {

            response.setStatus(
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().write(
                    "{"
                    + "\"servlet\":\"SEARCH_MVP_SERVLET_HIT\","
                    + "\"error\":\"Empty Elasticsearch response\","
                    + "\"query\":\""
                    + escapeJson(query.trim())
                    + "\""
                    + "}"
            );

            return;
        }

        /*
         * Return response
         */
        response.setStatus(
                SlingHttpServletResponse.SC_OK
        );

        response.getWriter().write(
                "{"
                + "\"servlet\":\"SEARCH_MVP_SERVLET_HIT\","
                + "\"query\":\""
                + escapeJson(query.trim())
                + "\","
                + "\"template\":\""
                + escapeJson(template)
                + "\","
                + "\"contentType\":\""
                + escapeJson(contentType)
                + "\","
                + "\"tag\":\""
                + escapeJson(tag)
                + "\","
                + "\"sort\":\""
                + escapeJson(sort)
                + "\","
                + "\"page\":"
                + page
                + ","
                + "\"size\":"
                + size
                + ","
                + "\"elasticsearch\":"
                + result
                + "}"
        );
    }

    /*
     * Validate allowed sorting options.
     */
    private boolean isValidSort(String sort) {

        return "relevance".equalsIgnoreCase(sort)
                || "lastModified".equalsIgnoreCase(sort)
                || "title".equalsIgnoreCase(sort);
    }

    private String escapeJson(String value) {

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