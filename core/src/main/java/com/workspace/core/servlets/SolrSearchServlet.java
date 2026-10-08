package com.workspace.core.servlets;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.workspace.core.models.SolrSearchResponse;
import com.workspace.core.services.SolrService;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = Servlet.class, property = {
        ServletResolverConstants.SLING_SERVLET_PATHS
                + "=/bin/workspace/solr/search",
        ServletResolverConstants.SLING_SERVLET_METHODS
                + "=GET"
})
public class SolrSearchServlet
        extends SlingSafeMethodsServlet {

    private static final long serialVersionUID = 1L;

    private static final String QUERY_PARAMETER = "q";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private SolrService solrService;

    @Override
    protected void doGet(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        String query = request.getParameter(
                QUERY_PARAMETER);

        /**
         * Validate the search query before
         * calling the Solr service.
         */
        if (query == null
                || query.trim().isEmpty()) {

            response.setStatus(
                    HttpServletResponse.SC_BAD_REQUEST);

            writeJson(
                    response,
                    "{\"error\":\"Missing required query parameter: q\"}");

            return;
        }

        try {

            /**
             * Delegate the actual search operation
             * to SolrService.
             */
            SolrSearchResponse searchResponse = solrService.searchPages(query);

            response.setStatus(
                    HttpServletResponse.SC_OK);

            writeJson(
                    response,
                    objectMapper.writeValueAsString(
                            searchResponse));

        } catch (SolrIndexException e) {

            response.setStatus(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            writeJson(
                    response,
                    "{\"error\":\""
                            + escapeJson(
                                    e.getMessage())
                            + "\"}");
        }
    }

    /**
     * Writes a JSON response with the correct
     * content type and character encoding.
     */
    private void writeJson(
            SlingHttpServletResponse response,
            String json)
            throws IOException {

        response.setContentType(
                "application/json");

        response.setCharacterEncoding(
                "UTF-8");

        response.getWriter().write(
                json);
    }

    /**
     * Escapes a string before placing it inside
     * a JSON error response.
     */
    private String escapeJson(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}