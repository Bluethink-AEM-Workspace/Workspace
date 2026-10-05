package com.workspace.core.servlets;

import com.workspace.core.services.impl.AlgoliaIndexingServiceImpl;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/workspace/search",
        "sling.servlet.methods=GET"
    }
)
public class AlgoliaSearchServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(AlgoliaSearchServlet.class);

    // Aap chahein toh Algolia credentials ke liye ek dedicated Search Service bhi bana sakte hain,
    // filhal hum indexing service ya direct config use kar sakte hain.
    @Reference
    private AlgoliaIndexingServiceImpl indexingService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) 
            throws ServletException, java.io.IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String query = request.getParameter("q");
        if (query == null || query.trim().isEmpty()) {
            response.getWriter().write("{\"error\": \"Query parameter 'q' is required\"}");
            return;
        }

        try {
            // Algolia Search API Endpoint (App ID aapke config se ya direct le sakte hain)
            // Note: Production mein Search-Only API key use karni chahiye, admin key nahi!
            String appId = "RCITT5UEN9"; // Aap isko config se bhi inject kara sakte hain
            String searchApiKey = "YOUR_SEARCH_ONLY_API_KEY"; // Yahan search-only key daalein
            String indexName = "aem_pages_index";

            String urlString = String.format("https://%s-dsn.algolia.net/1/indexes/%s?query=%s", 
                    appId, indexName, URLEncoder.encode(query, StandardCharsets.UTF_8.name()));

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Algolia-Application-Id", appId);
            conn.setRequestProperty("X-Algolia-API-Key", searchApiKey);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder content = new StringBuilder();
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    response.getWriter().write(content.toString());
                }
            } else {
                response.getWriter().write("{\"error\": \"Failed to fetch from Algolia, status code: " + responseCode + "\"}");
            }
            conn.disconnect();

        } catch (Exception e) {
            LOG.error("Error executing Algolia search query", e);
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}