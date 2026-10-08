package com.workspace.core.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.workspace.core.models.SolrSearchResponse;
import com.workspace.core.models.SolrSearchResult;
import com.workspace.core.services.SolrConfigurationService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;

class SolrServiceImplTest {

    private SolrServiceImpl solrService;

    private SolrConfigurationService solrConfigurationService;

    @BeforeEach
    void setUp() throws Exception {

        solrConfigurationService = mock(SolrConfigurationService.class);

        solrService = new SolrServiceImpl();

        Field field = SolrServiceImpl.class
                .getDeclaredField(
                        "solrConfigurationService");

        field.setAccessible(true);

        field.set(
                solrService,
                solrConfigurationService);
    }

    // ---------------------------------------------------------------------
    // Configuration delegation
    // ---------------------------------------------------------------------

    @Test
    void testGetIndexingTarget() {

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("aem");

        assertEquals(
                "aem",
                solrService.getIndexingTarget());
    }

    @Test
    void testIsAemIndexingTarget() {

        when(solrConfigurationService.isAemIndexingTarget())
                .thenReturn(true);

        assertTrue(
                solrService.isAemIndexingTarget());
    }

    @Test
    void testIsExternalIndexingTarget() {

        when(solrConfigurationService.isExternalIndexingTarget())
                .thenReturn(true);

        assertTrue(
                solrService.isExternalIndexingTarget());
    }

    // ---------------------------------------------------------------------
    // AEM indexing validation
    // ---------------------------------------------------------------------

    @Test
    void testIndexPageWithNullResource() {

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.indexPage(null));

        assertEquals(
                "Cannot index a null AEM resource",
                exception.getMessage());
    }

    @Test
    void testIndexPageSkipsWhenExternalTarget()
            throws Exception {

        when(solrConfigurationService.isAemIndexingTarget())
                .thenReturn(false);

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("external");

        Resource resource = mock(Resource.class);

        solrService.indexPage(resource);

        assertFalse(
                solrService.isAemIndexingTarget());
    }

    @Test
    void testIndexPageWhenResourceIsNotPage()
            throws Exception {

        when(solrConfigurationService.isAemIndexingTarget())
                .thenReturn(true);

        Resource resource = mock(Resource.class);

        ValueMap properties = mock(ValueMap.class);

        when(resource.getValueMap())
                .thenReturn(properties);

        when(properties.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("nt:unstructured");

        when(resource.getParent())
                .thenReturn(null);

        solrService.indexPage(resource);
    }

    @Test
    void testIndexPageFindsParentPage()
            throws Exception {

        when(solrConfigurationService.isAemIndexingTarget())
                .thenReturn(true);

        Resource textResource = mock(Resource.class);

        Resource pageResource = mock(Resource.class);

        ValueMap textProperties = mock(ValueMap.class);

        ValueMap pageProperties = mock(ValueMap.class);

        when(textResource.getPath())
                .thenReturn(
                        "/content/workspace/us/en/products/jcr:content/root/text");

        when(textResource.getValueMap())
                .thenReturn(textProperties);

        when(textProperties.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("nt:unstructured");

        when(textResource.getParent())
                .thenReturn(pageResource);

        when(pageResource.getValueMap())
                .thenReturn(pageProperties);

        when(pageProperties.get(
                "jcr:primaryType",
                String.class))
                .thenReturn("cq:Page");

        when(pageResource.getPath())
                .thenReturn(
                        "/content/workspace/us/en/products");

        when(pageResource.getName())
                .thenReturn("products");

        when(pageResource.getChild("jcr:content"))
                .thenReturn(null);

        when(solrConfigurationService.getSolrBaseUrl())
                .thenReturn(
                        "http://localhost:1");

        assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.indexPage(textResource));
    }

    // ---------------------------------------------------------------------
    // AEM page title/content helpers
    // ---------------------------------------------------------------------

    @Test
    void testGetPageTitleUsesJcrTitle()
            throws Exception {

        Resource page = mock(Resource.class);

        Resource content = mock(Resource.class);

        ValueMap contentProperties = mock(ValueMap.class);

        when(page.getChild("jcr:content"))
                .thenReturn(content);

        when(content.getValueMap())
                .thenReturn(contentProperties);

        when(contentProperties.get(
                "jcr:title",
                String.class))
                .thenReturn("Products");

        String result = invokePrivate(
                "getPageTitle",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testGetPageTitleFallsBackToPageName()
            throws Exception {

        Resource page = mock(Resource.class);

        Resource content = mock(Resource.class);

        ValueMap contentProperties = mock(ValueMap.class);

        when(page.getName())
                .thenReturn("products");

        when(page.getChild("jcr:content"))
                .thenReturn(content);

        when(content.getValueMap())
                .thenReturn(contentProperties);

        when(contentProperties.get(
                "jcr:title",
                String.class))
                .thenReturn(null);

        String result = invokePrivate(
                "getPageTitle",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "products",
                result);
    }

    @Test
    void testGetPageTitleFallsBackWhenTitleBlank()
            throws Exception {

        Resource page = mock(Resource.class);

        Resource content = mock(Resource.class);

        ValueMap properties = mock(ValueMap.class);

        when(page.getName())
                .thenReturn("about");

        when(page.getChild("jcr:content"))
                .thenReturn(content);

        when(content.getValueMap())
                .thenReturn(properties);

        when(properties.get(
                "jcr:title",
                String.class))
                .thenReturn("   ");

        String result = invokePrivate(
                "getPageTitle",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "about",
                result);
    }

    @Test
    void testGetPageTitleWhenContentMissing()
            throws Exception {

        Resource page = mock(Resource.class);

        when(page.getName())
                .thenReturn("about");

        when(page.getChild("jcr:content"))
                .thenReturn(null);

        String result = invokePrivate(
                "getPageTitle",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "about",
                result);
    }

    @Test
    void testGetPageContentWhenContentMissing()
            throws Exception {

        Resource page = mock(Resource.class);

        when(page.getChild("jcr:content"))
                .thenReturn(null);

        String result = invokePrivate(
                "getPageContent",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "",
                result);
    }

    @Test
    void testGetPageContentExtractsText()
            throws Exception {

        Resource content = mock(Resource.class);

        Resource text = mock(Resource.class);

        ValueMap textProperties = mock(ValueMap.class);

        when(content.getName())
                .thenReturn("jcr:content");

        when(content.getChildren())
                .thenReturn(
                        List.of(text));

        when(text.getName())
                .thenReturn("text");

        when(text.getValueMap())
                .thenReturn(textProperties);

        when(textProperties.get(
                "text",
                String.class))
                .thenReturn(
                        "<p>Hello <strong>World</strong></p>");

        when(text.getChildren())
                .thenReturn(
                        Collections.emptyList());

        Resource page = mock(Resource.class);

        when(page.getChild("jcr:content"))
                .thenReturn(content);

        String result = invokePrivate(
                "getPageContent",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "Hello World",
                result);
    }

    @Test
    void testGetPageContentCollectsMultipleTextComponents()
            throws Exception {

        Resource content = mock(Resource.class);

        Resource text1 = mock(Resource.class);

        Resource text2 = mock(Resource.class);

        ValueMap text1Properties = mock(ValueMap.class);

        ValueMap text2Properties = mock(ValueMap.class);

        when(content.getChildren())
                .thenReturn(
                        List.of(
                                text1,
                                text2));

        when(text1.getName())
                .thenReturn("text");

        when(text1.getValueMap())
                .thenReturn(text1Properties);

        when(text1Properties.get(
                "text",
                String.class))
                .thenReturn(
                        "<p>First paragraph</p>");

        when(text1.getChildren())
                .thenReturn(
                        Collections.emptyList());

        when(text2.getName())
                .thenReturn("text");

        when(text2.getValueMap())
                .thenReturn(text2Properties);

        when(text2Properties.get(
                "text",
                String.class))
                .thenReturn(
                        "<p>Second paragraph</p>");

        when(text2.getChildren())
                .thenReturn(
                        Collections.emptyList());

        Resource page = mock(Resource.class);

        when(page.getChild("jcr:content"))
                .thenReturn(content);

        String result = invokePrivate(
                "getPageContent",
                new Class<?>[] {
                        Resource.class
                },
                page);

        assertEquals(
                "First paragraph Second paragraph",
                result);
    }

    @Test
    void testStripHtml()
            throws Exception {

        String result = invokePrivate(
                "stripHtml",
                new Class<?>[] {
                        String.class
                },
                "<p>Hello <b>World</b></p>");

        assertEquals(
                "Hello World",
                result);
    }

    @Test
    void testStripHtmlWithNull()
            throws Exception {

        String result = invokePrivate(
                "stripHtml",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // External page indexing validation
    // ---------------------------------------------------------------------

    @Test
    void testIndexExternalPageWithNullPage() {

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.indexExternalPage(null));

        assertEquals(
                "Cannot index a null external page",
                exception.getMessage());
    }

    @Test
    void testIndexExternalPageSkipsForAemTarget()
            throws Exception {

        when(solrConfigurationService.isExternalIndexingTarget())
                .thenReturn(false);

        when(solrConfigurationService.getIndexingTarget())
                .thenReturn("aem");

        CrawledPage page = mock(CrawledPage.class);

        when(page.getUrl())
                .thenReturn(
                        "https://example.com");

        solrService.indexExternalPage(page);
    }

    @Test
    void testIndexExternalPageWithNullUrl() {

        when(solrConfigurationService.isExternalIndexingTarget())
                .thenReturn(true);

        CrawledPage page = mock(CrawledPage.class);

        when(page.getUrl())
                .thenReturn(null);

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.indexExternalPage(page));

        assertEquals(
                "External page URL cannot be empty",
                exception.getMessage());
    }

    @Test
    void testIndexExternalPageWithBlankUrl() {

        when(solrConfigurationService.isExternalIndexingTarget())
                .thenReturn(true);

        CrawledPage page = mock(CrawledPage.class);

        when(page.getUrl())
                .thenReturn("   ");

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.indexExternalPage(page));

        assertEquals(
                "External page URL cannot be empty",
                exception.getMessage());
    }

    @Test
    void testIndexExternalPageAttemptsSolrIndexing()
            throws Exception {

        when(solrConfigurationService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrConfigurationService.getSolrBaseUrl())
                .thenReturn(
                        "http://localhost:1");

        CrawledPage page = mock(CrawledPage.class);

        when(page.getUrl())
                .thenReturn(
                        "https://example.com/products");

        when(page.getTitle())
                .thenReturn("Products");

        when(page.getContent())
                .thenReturn("Product content");

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.indexExternalPage(page));

        assertNotNull(exception);
    }

    // ---------------------------------------------------------------------
    // safeValue
    // ---------------------------------------------------------------------

    @Test
    void testSafeValueReturnsOriginalValue()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "Products",
                "Untitled");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testSafeValueUsesDefaultForNull()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                null,
                "Untitled");

        assertEquals(
                "Untitled",
                result);
    }

    @Test
    void testSafeValueUsesDefaultForBlank()
            throws Exception {

        String result = invokePrivate(
                "safeValue",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                "   ",
                "Untitled");

        assertEquals(
                "Untitled",
                result);
    }

    // ---------------------------------------------------------------------
    // Solr delete validation / HTTP failure
    // ---------------------------------------------------------------------

    @Test
    void testDeleteDocumentWithNullId() {

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.deleteDocument(null));

        assertEquals(
                "Solr document ID cannot be empty",
                exception.getMessage());
    }

    @Test
    void testDeleteDocumentWithBlankId() {

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.deleteDocument("   "));

        assertEquals(
                "Solr document ID cannot be empty",
                exception.getMessage());
    }

    @Test
    void testDeleteDocumentCommunicationFailure()
            throws Exception {

        when(solrConfigurationService.getSolrBaseUrl())
                .thenReturn(
                        "http://localhost:1");

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.deleteDocument("test-id"));

        assertEquals(
                "Failed to communicate with Solr",
                exception.getMessage());
    }

    // ---------------------------------------------------------------------
    // Search query construction
    // ---------------------------------------------------------------------

    @Test
    void testBuildSearchQuery()
            throws Exception {

        String result = invokePrivate(
                "buildSearchQuery",
                new Class<?>[] {
                        String.class
                },
                "products");

        assertEquals(
                "pagePath:products OR pageTitle:products "
                        + "OR pageContent:products OR pageUrl:products",
                result);
    }

    @Test
    void testBuildSearchQueryEscapesSpecialCharacters()
            throws Exception {

        String result = invokePrivate(
                "buildSearchQuery",
                new Class<?>[] {
                        String.class
                },
                "a+b");

        assertTrue(
                result.contains(
                        "a\\+b"));
    }

    @Test
    void testEscapeSolrQuery()
            throws Exception {

        String result = invokePrivate(
                "escapeSolrQuery",
                new Class<?>[] {
                        String.class
                },
                "a+b-c");

        assertEquals(
                "a\\+b\\-c",
                result);
    }

    @Test
    void testEscapeSolrQueryWithNull()
            throws Exception {

        String result = invokePrivate(
                "escapeSolrQuery",
                new Class<?>[] {
                        String.class
                },
                new Object[] {
                        null
                });

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // Search response parsing
    // ---------------------------------------------------------------------

    @Test
    void testParseSearchResponseWithResults()
            throws Exception {

        String response = "{"
                + "\"response\":{"
                + "\"numFound\":2,"
                + "\"docs\":["
                + "{"
                + "\"id\":\"/content/page1\","
                + "\"pagePath\":\"/content/page1\","
                + "\"pageUrl\":\"\","
                + "\"pageTitle\":\"Page One\","
                + "\"pageContent\":\"First content\","
                + "\"source\":\"\""
                + "},"
                + "{"
                + "\"id\":\"/content/page2\","
                + "\"pagePath\":\"/content/page2\","
                + "\"pageUrl\":\"\","
                + "\"pageTitle\":\"Page Two\","
                + "\"pageContent\":\"Second content\","
                + "\"source\":\"\""
                + "}"
                + "]"
                + "}"
                + "}";

        SolrSearchResponse result = invokePrivate(
                "parseSearchResponse",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                response,
                "products");

        assertNotNull(result);

        assertEquals(
                "products",
                result.getQuery());

        assertEquals(
                2,
                result.getTotalResults());

        assertEquals(
                2,
                result.getResults().size());

        SolrSearchResult first = result.getResults().get(0);

        assertEquals(
                "/content/page1",
                first.getId());

        assertEquals(
                "/content/page1",
                first.getPagePath());

        assertEquals(
                "",
                first.getPageUrl());

        assertEquals(
                "Page One",
                first.getPageTitle());

        assertEquals(
                "First content",
                first.getPageContent());

        assertEquals(
                "",
                first.getSource());
    }

    @Test
    void testParseSearchResponseWithEmptyDocs()
            throws Exception {

        String response = "{"
                + "\"response\":{"
                + "\"numFound\":5,"
                + "\"docs\":[]"
                + "}"
                + "}";

        SolrSearchResponse result = invokePrivate(
                "parseSearchResponse",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                response,
                "products");

        assertEquals(
                5,
                result.getTotalResults());

        assertTrue(
                result.getResults().isEmpty());
    }

    @Test
    void testParseSearchResponseWithMissingFields()
            throws Exception {

        String response = "{"
                + "\"response\":{"
                + "\"numFound\":1,"
                + "\"docs\":[{}]"
                + "}"
                + "}";

        SolrSearchResponse result = invokePrivate(
                "parseSearchResponse",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                response,
                "test");

        assertEquals(
                1,
                result.getResults().size());

        SolrSearchResult resultItem = result.getResults().get(0);

        assertEquals(
                "",
                resultItem.getId());

        assertEquals(
                "",
                resultItem.getPagePath());

        assertEquals(
                "",
                resultItem.getPageUrl());

        assertEquals(
                "",
                resultItem.getPageTitle());

        assertEquals(
                "",
                resultItem.getPageContent());

        assertEquals(
                "",
                resultItem.getSource());
    }

    @Test
    void testParseSearchResponseWithArrayFields()
            throws Exception {

        String response = "{"
                + "\"response\":{"
                + "\"numFound\":1,"
                + "\"docs\":["
                + "{"
                + "\"id\":[\"id1\"],"
                + "\"pagePath\":[\"/content/page1\"],"
                + "\"pageUrl\":[\"https://example.com\"],"
                + "\"pageTitle\":[\"Title One\",\"Title Two\"],"
                + "\"pageContent\":[\"Hello\",\"World\"],"
                + "\"source\":[\"external\"]"
                + "}"
                + "]"
                + "}"
                + "}";

        SolrSearchResponse result = invokePrivate(
                "parseSearchResponse",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                response,
                "hello");

        SolrSearchResult resultItem = result.getResults().get(0);

        assertEquals(
                "id1",
                resultItem.getId());

        assertEquals(
                "/content/page1",
                resultItem.getPagePath());

        assertEquals(
                "https://example.com",
                resultItem.getPageUrl());

        assertEquals(
                "Title One Title Two",
                resultItem.getPageTitle());

        assertEquals(
                "Hello World",
                resultItem.getPageContent());

        assertEquals(
                "external",
                resultItem.getSource());
    }

    @Test
    void testParseSearchResponseWithNullArrayItems()
            throws Exception {

        String response = "{"
                + "\"response\":{"
                + "\"numFound\":1,"
                + "\"docs\":["
                + "{"
                + "\"pageTitle\":[null,\"Title\",\"\"]"
                + "}"
                + "]"
                + "}"
                + "}";

        SolrSearchResponse result = invokePrivate(
                "parseSearchResponse",
                new Class<?>[] {
                        String.class,
                        String.class
                },
                response,
                "test");

        assertEquals(
                "Title",
                result.getResults()
                        .get(0)
                        .getPageTitle());
    }

    @Test
    void testParseSearchResponseWithInvalidJson() {

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> invokePrivate(
                        "parseSearchResponse",
                        new Class<?>[] {
                                String.class,
                                String.class
                        },
                        "invalid-json",
                        "test"));

        assertEquals(
                "Failed to parse Solr search response",
                exception.getMessage());

        assertNotNull(
                exception.getCause());
    }

    // ---------------------------------------------------------------------
    // getFieldText
    // ---------------------------------------------------------------------

    @Test
    void testGetFieldTextWithScalar()
            throws Exception {

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.JsonNode document = mapper.readTree(
                "{\"pageTitle\":\"Products\"}");

        String result = invokePrivate(
                "getFieldText",
                new Class<?>[] {
                        com.fasterxml.jackson.databind.JsonNode.class,
                        String.class
                },
                document,
                "pageTitle");

        assertEquals(
                "Products",
                result);
    }

    @Test
    void testGetFieldTextWithArray()
            throws Exception {

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.JsonNode document = mapper.readTree(
                "{\"pageTitle\":[\"Products\",\"AEM\"]}");

        String result = invokePrivate(
                "getFieldText",
                new Class<?>[] {
                        com.fasterxml.jackson.databind.JsonNode.class,
                        String.class
                },
                document,
                "pageTitle");

        assertEquals(
                "Products AEM",
                result);
    }

    @Test
    void testGetFieldTextWithMissingField()
            throws Exception {

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.JsonNode document = mapper.readTree(
                "{}");

        String result = invokePrivate(
                "getFieldText",
                new Class<?>[] {
                        com.fasterxml.jackson.databind.JsonNode.class,
                        String.class
                },
                document,
                "pageTitle");

        assertEquals(
                "",
                result);
    }

    // ---------------------------------------------------------------------
    // HTTP helper error handling
    // ---------------------------------------------------------------------

    @Test
    void testSearchPagesCommunicationFailure()
            throws Exception {

        when(solrConfigurationService.getSolrBaseUrl())
                .thenReturn(
                        "http://localhost:1");

        SolrServiceImpl.SolrIndexException exception = assertThrows(
                SolrServiceImpl.SolrIndexException.class,
                () -> solrService.searchPages("products"));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "Failed to search Solr"));
    }

    // ---------------------------------------------------------------------
    // Reflection helper
    // ---------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> T invokePrivate(
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments)
            throws Exception {

        Method method = SolrServiceImpl.class
                .getDeclaredMethod(
                        methodName,
                        parameterTypes);

        method.setAccessible(true);

        try {

            return (T) method.invoke(
                    solrService,
                    arguments);

        } catch (InvocationTargetException e) {

            Throwable cause = e.getCause();

            if (cause instanceof Exception) {
                throw (Exception) cause;
            }

            if (cause instanceof Error) {
                throw (Error) cause;
            }

            throw e;
        }
    }
}
