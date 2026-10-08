package com.workspace.core.servlets;

import com.workspace.core.services.ElasticsearchService;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;

@Component(
        service = Servlet.class,
        property = {
                "sling.servlet.paths=/bin/workspace/elasticsearch/health",
                "sling.servlet.methods=GET"
        }
)
public class ElasticsearchHealthServlet extends SlingSafeMethodsServlet {

    @Reference
    private ElasticsearchService elasticsearchService;

    @Override
    protected void doGet(
            SlingHttpServletRequest request,
            SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (elasticsearchService.isHealthy()) {

            response.setStatus(SlingHttpServletResponse.SC_OK);

            response.getWriter().write(
                    "{\"status\":\"UP\",\"elasticsearch\":\"connected\"}"
            );

        } else {

            response.setStatus(
                    SlingHttpServletResponse.SC_SERVICE_UNAVAILABLE
            );

            response.getWriter().write(
                    "{\"status\":\"DOWN\",\"elasticsearch\":\"unavailable\"}"
            );
        }
    }
}