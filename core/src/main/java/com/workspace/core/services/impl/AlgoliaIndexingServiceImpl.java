package com.workspace.core.services.impl;

import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component(service = AlgoliaIndexingServiceImpl.class, immediate = true)
@Designate(ocd = AlgoliaConfig.class)
public class AlgoliaIndexingServiceImpl {

    private static final Logger LOG = LoggerFactory.getLogger(AlgoliaIndexingServiceImpl.class);

    private static final int CONNECTION_TIMEOUT = 5000; // 5 Seconds
    private static final int READ_TIMEOUT = 10000;       // 10 Seconds

    private String appId;
    private String apiKey;        // Write / Admin API Key (Backend POST & DELETE APIs)
    private String searchApiKey;  // Public Search-Only API Key (Frontend JS)
    private String indexName;

    @Reference
    private PageContentExtractorImpl pageContentExtractor;

    @Reference
    private ResourceResolverFactory resolverFactory;

   
   @Activate
@Modified
protected void activate(AlgoliaConfig config) {
    this.appId = config.appId();
    this.apiKey = config.adminApiKey(); // <-- Yahan apiKey() ko adminApiKey() karein
    this.searchApiKey = config.searchApiKey();
    this.indexName = config.indexName();
    LOG.info("AlgoliaIndexingServiceImpl activated with App ID: [{}] and Index: [{}]", appId, indexName);
}

    public void indexPage(String pagePath) {
        if (StringUtils.isBlank(pagePath)) return;

        Map<String, Object> param = new HashMap<>();
        param.put(ResourceResolverFactory.SUBSERVICE, "workspaceService");

        try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(param)) {
            indexPage(pagePath, resolver);
        } catch (Exception e) {
            LOG.error("CRITICAL: Service User Resolver Failed for 'workspaceService'. Error: {}", e.getMessage(), e);
        }
    }

    public void indexPage(String pagePath, ResourceResolver resolver) {
        if (StringUtils.isBlank(pagePath) || resolver == null) return;
        PageManager pageManager = resolver.adaptTo(PageManager.class);
        if (pageManager != null) {
            Page page = pageManager.getPage(pagePath);
            if (page != null) {
                indexPage(page);
            } else {
                LOG.warn("Page object is null for path: {}", pagePath);
            }
        }
    }

    public void indexPage(Page page) {
        if (page == null || StringUtils.isAnyBlank(appId, apiKey, indexName)) {
            LOG.error("Algolia service is not properly configured or Page object is null.");
            return;
        }

        try {
            Map<String, Object> pageData = pageContentExtractor.getPageDataForAlgolia(page);
            ObjectMapper mapper = new ObjectMapper();
            String jsonPayload = mapper.writeValueAsString(pageData);

            String apiUrl = String.format("https://%s-dsn.algolia.net/1/indexes/%s", appId, indexName);
            URL url = new URL(apiUrl);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("X-Algolia-Application-Id", appId);
            conn.setRequestProperty("X-Algolia-API-Key", apiKey); // Backend Write Key
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                LOG.info("Successfully indexed page [{}] into Algolia. Response Code: {}", page.getPath(), responseCode);
            } else {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    LOG.error("Algolia Indexing Failed for [{}] - Response Code: {}, Error: {}", page.getPath(), responseCode, response);
                }
            }

        } catch (Exception e) {
            LOG.error("Error occurred while indexing page [{}] to Algolia", page.getPath(), e);
        }
    }

    public void deletePage(String pagePath) {
        if (StringUtils.isBlank(pagePath) || StringUtils.isAnyBlank(appId, apiKey, indexName)) return;

        try {
            String encodedObjectId = URLEncoder.encode(pagePath, StandardCharsets.UTF_8.name());
            String apiUrl = String.format("https://%s-dsn.algolia.net/1/indexes/%s/%s", appId, indexName, encodedObjectId);

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("X-Algolia-Application-Id", appId);
            conn.setRequestProperty("X-Algolia-API-Key", apiKey); // Backend Write Key

            int responseCode = conn.getResponseCode();
            LOG.info("Algolia Delete Response Code for [{}]: {}", pagePath, responseCode);

        } catch (Exception e) {
            LOG.error("Error deleting page [{}] from Algolia", pagePath, e);
        }
    }

    // =========================================================================
    // Getters for Sling Model / Frontend HTL integration
    // =========================================================================

    public String getAppId() {
        return appId;
    }

    public String getSearchApiKey() {
        return searchApiKey;
    }

    public String getIndexName() {
        return indexName;
    }
}