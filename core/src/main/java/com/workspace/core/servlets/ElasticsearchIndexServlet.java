package com.workspace.core.servlets;

import com.workspace.core.services.ElasticsearchService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/workspace/elasticsearch/index",
                "sling.servlet.methods=POST"
        }
)
public class ElasticsearchIndexServlet
        extends SlingAllMethodsServlet {

    @Reference
    private ElasticsearchService elasticsearchService;


    @Override
    protected void doPost(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response
    ) throws IOException {

        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );


        /*
         * Create a new versioned index.
         */
        String newIndex =
                elasticsearchService.createVersionedIndex();


        if (newIndex == null) {

            response.setStatus(
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().write(
                    "{"
                    + "\"status\":\"ERROR\","
                    + "\"message\":\"Unable to create versioned Elasticsearch index\""
                    + "}"
            );

            return;
        }


        /*
         * Switch stable alias to the new index.
         */
        boolean aliasSwitched =
                elasticsearchService.switchAlias(
                        elasticsearchService.getIndexAlias(),
                        newIndex
                );


        if (!aliasSwitched) {

            response.setStatus(
                    SlingHttpServletResponse.SC_INTERNAL_SERVER_ERROR
            );

            response.getWriter().write(
                    "{"
                    + "\"status\":\"ERROR\","
                    + "\"message\":\"Index created but alias switch failed\","
                    + "\"index\":\""
                    + escapeJson(newIndex)
                    + "\""
                    + "}"
            );

            return;
        }


        /*
         * Success.
         */
        response.setStatus(
                SlingHttpServletResponse.SC_OK
        );

        response.getWriter().write(
                "{"
                + "\"status\":\"SUCCESS\","
                + "\"message\":\"Elasticsearch index created and alias switched successfully\","
                + "\"index\":\""
                + escapeJson(newIndex)
                + "\","
                + "\"alias\":\""
                + escapeJson(
                        elasticsearchService.getIndexAlias()
                )
                + "\""
                + "}"
        );
    }


    private String escapeJson(
            String value
    ) {

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
                );
    }
}