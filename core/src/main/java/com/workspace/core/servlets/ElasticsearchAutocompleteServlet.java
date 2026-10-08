package com.workspace.core.servlets;

import com.workspace.core.services.ElasticsearchService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/workspace/elasticsearch/autocomplete",
                "sling.servlet.methods=GET"
        }
)
public class ElasticsearchAutocompleteServlet
        extends SlingSafeMethodsServlet {

    @Reference
    private ElasticsearchService elasticsearchService;


    @Override
    protected void doGet(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response
    ) throws IOException {


        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );


        String query =
                request.getParameter(
                        "q"
                );


        int size =
                10;


        String sizeParameter =
                request.getParameter(
                        "size"
                );


        if (sizeParameter != null) {

            try {

                size =
                        Integer.parseInt(
                                sizeParameter
                        );

            } catch (NumberFormatException e) {

                size = 10;
            }
        }


        if (size <= 0) {

            size = 10;
        }


        if (size > 20) {

            size = 20;
        }


        String result =
                elasticsearchService.autocomplete(
                        query,
                        size
                );


        response.setStatus(
                SlingHttpServletResponse.SC_OK
        );


        response.getWriter().write(
                result
        );
    }
}