package com.workspace.core.services.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.workspace.core.models.SolrSearchResponse;
import com.workspace.core.models.SolrSearchResult;
import com.workspace.core.services.SolrConfigurationService;
import com.workspace.core.services.SolrService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = SolrService.class)
public class SolrServiceImpl implements SolrService {

        private static final Logger LOG = LoggerFactory.getLogger(SolrServiceImpl.class);

        private static final String JCR_CONTENT = "jcr:content";
        private static final String JCR_TITLE = "jcr:title";

        private static final String TEXT_RESOURCE_NAME = "text";
        private static final String TEXT_PROPERTY = "text";

        private static final String FIELD_ID = "id";
        private static final String FIELD_PAGE_PATH = "pagePath";
        private static final String FIELD_PAGE_URL = "pageUrl";
        private static final String FIELD_PAGE_TITLE = "pageTitle";
        private static final String FIELD_PAGE_CONTENT = "pageContent";
        private static final String FIELD_SOURCE = "source";

        private static final String SOURCE_EXTERNAL = "external";

        private static final String RESPONSE_STATUS = "response";
        private static final String RESPONSE_NUM_FOUND = "numFound";
        private static final String RESPONSE_DOCS = "docs";

        private static final String DEFAULT_PAGE_TITLE = "Untitled";

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Reference
        private SolrConfigurationService solrConfigurationService;

        @Override
        public void indexPage(Resource resource)
                        throws SolrIndexException {

                if (resource == null) {
                        throw new SolrIndexException(
                                        "Cannot index a null AEM resource");
                }

                if (!isAemIndexingTarget()) {
                        LOG.debug(
                                        "Skipping AEM page indexing because "
                                                        + "the configured Solr indexing target is external: {}",
                                        getIndexingTarget());
                        return;
                }

                Resource pageResource = findPageResource(resource);

                if (pageResource == null) {
                        LOG.debug(
                                        "No cq:Page found for resource: {}",
                                        resource.getPath());
                        return;
                }

                String pagePath = pageResource.getPath();

                String pageTitle = getPageTitle(pageResource);

                String pageContent = getPageContent(pageResource);

                ObjectNode document = objectMapper.createObjectNode();

                document.put(
                                FIELD_ID,
                                pagePath);

                document.put(
                                FIELD_PAGE_PATH,
                                pagePath);

                document.put(
                                FIELD_PAGE_TITLE,
                                pageTitle);

                document.put(
                                FIELD_PAGE_CONTENT,
                                pageContent);

                indexDocument(document);

                LOG.info(
                                "Successfully indexed page in Solr: {}",
                                pagePath);
        }

        @Override
        public void indexExternalPage(CrawledPage page)
                        throws SolrIndexException {

                if (page == null) {
                        throw new SolrIndexException(
                                        "Cannot index a null external page");
                }

                if (!isExternalIndexingTarget()) {
                        LOG.debug(
                                        "Skipping external page indexing because "
                                                        + "the configured Solr indexing target is not external: {}",
                                        getIndexingTarget());
                        return;
                }

                String pageUrl = page.getUrl();

                if (pageUrl == null
                                || pageUrl.trim().isEmpty()) {

                        throw new SolrIndexException(
                                        "External page URL cannot be empty");
                }

                ObjectNode document = objectMapper.createObjectNode();

                /*
                 * External page URL is the Solr unique key.
                 */
                document.put(
                                FIELD_ID,
                                pageUrl);

                /*
                 * pagePath is required by the current schema.
                 */
                document.put(
                                FIELD_PAGE_PATH,
                                pageUrl);

                document.put(
                                FIELD_PAGE_URL,
                                pageUrl);

                document.put(
                                FIELD_PAGE_TITLE,
                                safeValue(
                                                page.getTitle(),
                                                DEFAULT_PAGE_TITLE));

                document.put(
                                FIELD_PAGE_CONTENT,
                                safeValue(
                                                page.getContent(),
                                                ""));

                document.put(
                                FIELD_SOURCE,
                                SOURCE_EXTERNAL);

                indexDocument(document);

                LOG.info(
                                "Successfully indexed external page in Solr: {}",
                                pageUrl);
        }

        @Override
        public void deleteDocument(String documentId)
                        throws SolrIndexException {

                if (documentId == null
                                || documentId.trim().isEmpty()) {

                        throw new SolrIndexException(
                                        "Solr document ID cannot be empty");
                }

                String solrUrl = solrConfigurationService.getSolrBaseUrl()
                                + "/update?commit=true";

                ObjectNode deleteDocument = objectMapper.createObjectNode();

                ObjectNode deleteNode = objectMapper.createObjectNode();

                deleteNode.put(
                                FIELD_ID,
                                documentId);

                deleteDocument.set(
                                "delete",
                                deleteNode);

                try {

                        String json = objectMapper.writeValueAsString(
                                        deleteDocument);

                        sendPostRequest(
                                        solrUrl,
                                        json);

                        LOG.info(
                                        "Successfully deleted document from Solr: {}",
                                        documentId);

                } catch (Exception e) {

                        if (e instanceof SolrIndexException) {
                                throw (SolrIndexException) e;
                        }

                        throw new SolrIndexException(
                                        "Failed to delete document from Solr: "
                                                        + documentId,
                                        e);
                }
        }

        @Override
        public String getIndexingTarget() {
                return solrConfigurationService
                                .getIndexingTarget();
        }

        @Override
        public boolean isAemIndexingTarget() {
                return solrConfigurationService
                                .isAemIndexingTarget();
        }

        @Override
        public boolean isExternalIndexingTarget() {
                return solrConfigurationService
                                .isExternalIndexingTarget();
        }

        @Override
        public SolrSearchResponse searchPages(
                        String query)
                        throws SolrIndexException {

                if (query == null
                                || query.trim().isEmpty()) {

                        query = "*:*";
                }

                try {

                        URIBuilder uriBuilder = new URIBuilder(
                                        solrConfigurationService
                                                        .getSolrBaseUrl()
                                                        + "/select");

                        uriBuilder.addParameter(
                                        "q",
                                        buildSearchQuery(query));

                        uriBuilder.addParameter(
                                        "wt",
                                        "json");

                        uriBuilder.addParameter(
                                        "rows",
                                        "50");

                        HttpGet request = new HttpGet(
                                        uriBuilder.build());

                        try (
                                        CloseableHttpClient client = HttpClients.createDefault();

                                        CloseableHttpResponse response = client.execute(request)) {

                                int statusCode = response.getStatusLine()
                                                .getStatusCode();

                                String responseBody = getResponseBody(response);

                                if (statusCode < 200
                                                || statusCode >= 300) {

                                        throw new SolrIndexException(
                                                        "Solr search failed. HTTP status: "
                                                                        + statusCode
                                                                        + ", response: "
                                                                        + responseBody);
                                }

                                return parseSearchResponse(
                                                responseBody,
                                                query);
                        }

                } catch (Exception e) {

                        if (e instanceof SolrIndexException) {
                                throw (SolrIndexException) e;
                        }

                        throw new SolrIndexException(
                                        "Failed to search Solr",
                                        e);
                }
        }

        private void indexDocument(
                        ObjectNode document)
                        throws SolrIndexException {

                try {

                        String json = objectMapper.writeValueAsString(
                                        document);

                        String solrUrl = solrConfigurationService
                                        .getSolrBaseUrl()
                                        + "/update/json/docs?commit=true";

                        LOG.debug(
                                        "Sending document to Solr: {}",
                                        document.path(
                                                        FIELD_ID).asText());

                        sendPostRequest(
                                        solrUrl,
                                        json);

                } catch (Exception e) {

                        if (e instanceof SolrIndexException) {
                                throw (SolrIndexException) e;
                        }

                        throw new SolrIndexException(
                                        "Failed to index document in Solr",
                                        e);
                }
        }

        private void sendPostRequest(
                        String url,
                        String json)
                        throws SolrIndexException {

                HttpPost request = new HttpPost(url);

                request.setHeader(
                                "Content-Type",
                                "application/json");

                request.setEntity(
                                new StringEntity(
                                                json,
                                                ContentType.APPLICATION_JSON));

                try (
                                CloseableHttpClient client = HttpClients.createDefault();

                                CloseableHttpResponse response = client.execute(request)) {

                        int statusCode = response.getStatusLine()
                                        .getStatusCode();

                        String responseBody = getResponseBody(response);

                        if (statusCode < 200
                                        || statusCode >= 300) {

                                throw new SolrIndexException(
                                                "Solr request failed. HTTP status: "
                                                                + statusCode
                                                                + ", response: "
                                                                + responseBody);
                        }

                        LOG.debug(
                                        "Solr request successful. HTTP status: {}",
                                        statusCode);

                } catch (IOException e) {

                        throw new SolrIndexException(
                                        "Failed to communicate with Solr",
                                        e);
                }
        }

        private String getResponseBody(
                        CloseableHttpResponse response)
                        throws IOException {

                HttpEntity entity = response.getEntity();

                if (entity == null) {
                        return "";
                }

                return EntityUtils.toString(
                                entity,
                                StandardCharsets.UTF_8);
        }

        private Resource findPageResource(
                        Resource resource) {

                Resource current = resource;

                while (current != null) {

                        ValueMap properties = current.getValueMap();

                        String primaryType = properties.get(
                                        "jcr:primaryType",
                                        String.class);

                        if ("cq:Page".equals(
                                        primaryType)) {

                                return current;
                        }

                        current = current.getParent();
                }

                return null;
        }

        private String getPageTitle(
                        Resource pageResource) {

                Resource contentResource = pageResource.getChild(
                                JCR_CONTENT);

                if (contentResource == null) {
                        return pageResource.getName();
                }

                ValueMap properties = contentResource.getValueMap();

                String title = properties.get(
                                JCR_TITLE,
                                String.class);

                if (title == null
                                || title.trim().isEmpty()) {

                        return pageResource.getName();
                }

                return title;
        }

        private String getPageContent(
                        Resource pageResource) {

                Resource contentResource = pageResource.getChild(
                                JCR_CONTENT);

                if (contentResource == null) {
                        return "";
                }

                StringBuilder content = new StringBuilder();

                collectTextContent(
                                contentResource,
                                content);

                return content.toString().trim();
        }

        private void collectTextContent(
                        Resource resource,
                        StringBuilder content) {

                if (TEXT_RESOURCE_NAME.equals(
                                resource.getName())) {

                        String text = resource.getValueMap().get(
                                        TEXT_PROPERTY,
                                        String.class);

                        if (text != null
                                        && !text.trim().isEmpty()) {

                                content.append(
                                                stripHtml(text))
                                                .append(" ");
                        }
                }

                for (Resource child : resource.getChildren()) {

                        collectTextContent(
                                        child,
                                        content);
                }
        }

        private String stripHtml(
                        String html) {

                if (html == null) {
                        return "";
                }

                return html
                                .replaceAll(
                                                "<[^>]*>",
                                                " ")
                                .replaceAll(
                                                "\\s+",
                                                " ")
                                .trim();
        }

        private String buildSearchQuery(
                        String query) {

                String escapedQuery = escapeSolrQuery(query);

                return FIELD_PAGE_PATH
                                + ":"
                                + escapedQuery
                                + " OR "
                                + FIELD_PAGE_TITLE
                                + ":"
                                + escapedQuery
                                + " OR "
                                + FIELD_PAGE_CONTENT
                                + ":"
                                + escapedQuery
                                + " OR "
                                + FIELD_PAGE_URL
                                + ":"
                                + escapedQuery;
        }

        private String escapeSolrQuery(
                        String query) {

                if (query == null) {
                        return "";
                }

                return query
                                .replace("\\", "\\\\")
                                .replace("+", "\\+")
                                .replace("-", "\\-")
                                .replace("&&", "\\&&")
                                .replace("||", "\\||")
                                .replace("!", "\\!")
                                .replace("(", "\\(")
                                .replace(")", "\\)")
                                .replace("{", "\\{")
                                .replace("}", "\\}")
                                .replace("[", "\\[")
                                .replace("]", "\\]")
                                .replace("^", "\\^")
                                .replace("\"", "\\\"")
                                .replace("~", "\\~")
                                .replace("*", "\\*")
                                .replace("?", "\\?")
                                .replace(":", "\\:");
        }

        private SolrSearchResponse parseSearchResponse(
                        String responseBody,
                        String query)
                        throws SolrIndexException {

                try {

                        JsonNode root = objectMapper.readTree(
                                        responseBody);

                        JsonNode responseNode = root.path(
                                        RESPONSE_STATUS);

                        long numFound = responseNode
                                        .path(
                                                        RESPONSE_NUM_FOUND)
                                        .asLong();

                        List<SolrSearchResult> results = new ArrayList<>();

                        JsonNode docsNode = responseNode.path(
                                        RESPONSE_DOCS);

                        if (docsNode.isArray()) {

                                for (JsonNode document : docsNode) {

                                        String id = getFieldText(
                                                        document,
                                                        FIELD_ID);

                                        String pagePath = getFieldText(
                                                        document,
                                                        FIELD_PAGE_PATH);

                                        String pageUrl = getFieldText(
                                                        document,
                                                        FIELD_PAGE_URL);

                                        String pageTitle = getFieldText(
                                                        document,
                                                        FIELD_PAGE_TITLE);

                                        String pageContent = getFieldText(
                                                        document,
                                                        FIELD_PAGE_CONTENT);

                                        String source = getFieldText(
                                                        document,
                                                        FIELD_SOURCE);

                                        results.add(
                                                        new SolrSearchResult(
                                                                        id,
                                                                        pagePath,
                                                                        pageUrl,
                                                                        pageTitle,
                                                                        pageContent,
                                                                        source));
                                }
                        }

                        return new SolrSearchResponse(
                                        query,
                                        numFound,
                                        results);

                } catch (Exception e) {

                        throw new SolrIndexException(
                                        "Failed to parse Solr search response",
                                        e);
                }
        }

        /**
         * Converts a Solr field into a String whether Solr
         * returns the field as a scalar or a multi-valued array.
         */
        private String getFieldText(
                        JsonNode document,
                        String fieldName) {

                JsonNode fieldNode = document.path(
                                fieldName);

                if (fieldNode.isMissingNode()
                                || fieldNode.isNull()) {

                        return "";
                }

                if (fieldNode.isArray()) {

                        StringBuilder value = new StringBuilder();

                        for (JsonNode item : fieldNode) {

                                if (item == null
                                                || item.isNull()) {
                                        continue;
                                }

                                String itemText = item.asText("");

                                if (itemText == null
                                                || itemText.trim().isEmpty()) {
                                        continue;
                                }

                                if (value.length() > 0) {
                                        value.append(" ");
                                }

                                value.append(
                                                itemText.trim());
                        }

                        return value.toString();
                }

                return fieldNode.asText("");
        }

        private String safeValue(
                        String value,
                        String defaultValue) {

                if (value == null
                                || value.trim().isEmpty()) {

                        return defaultValue;
                }

                return value;
        }

        public static class SolrIndexException
                        extends Exception {

                private static final long serialVersionUID = 1L;

                public SolrIndexException(
                                String message) {

                        super(message);
                }

                public SolrIndexException(
                                String message,
                                Throwable cause) {

                        super(message, cause);
                }
        }
}