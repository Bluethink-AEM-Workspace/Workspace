package com.workspace.core.servlets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.workspace.core.services.SolrService;
import com.workspace.core.services.WebsiteCrawlerService;
import com.workspace.core.services.WebsiteCrawlerService.CrawledPage;
import com.workspace.core.services.WebsiteCrawlerService.WebsiteCrawlerException;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

class ExternalSolrIndexServletTest {

    private ExternalSolrIndexServlet servlet;

    private WebsiteCrawlerService websiteCrawlerService;

    private SolrService solrService;

    private SlingHttpServletRequest request;

    private SlingHttpServletResponse response;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {

        servlet = new ExternalSolrIndexServlet();

        websiteCrawlerService = mock(
                WebsiteCrawlerService.class);

        solrService = mock(
                SolrService.class);

        request = mock(
                SlingHttpServletRequest.class);

        response = mock(
                SlingHttpServletResponse.class);

        responseWriter = new StringWriter();

        PrintWriter printWriter = new PrintWriter(
                responseWriter);

        when(response.getWriter())
                .thenReturn(printWriter);

        injectDependency(
                "websiteCrawlerService",
                websiteCrawlerService);

        injectDependency(
                "solrService",
                solrService);
    }

    // ---------------------------------------------------------------------
    // External indexing validation
    // ---------------------------------------------------------------------

    @Test
    void testDoPostReturnsBadRequestWhenExternalIndexingIsDisabled()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "http://localhost:8983/solr/workspace");

        servlet.doPost(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_BAD_REQUEST);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"error\""));

        assertTrue(
                body.contains(
                        "Configured indexing target is not an external website"));

        assertTrue(
                body.contains(
                        "\"indexingTarget\":\"http://localhost:8983/solr/workspace\""));

        verifyNoInteractions(
                websiteCrawlerService);
    }

    // ---------------------------------------------------------------------
    // Successful indexing
    // ---------------------------------------------------------------------

    @Test
    void testDoPostSuccessfullyCrawlsAndIndexesPages()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        CrawledPage firstPage = mock(
                CrawledPage.class);

        CrawledPage secondPage = mock(
                CrawledPage.class);

        when(firstPage.getUrl())
                .thenReturn(
                        "https://example.com/");

        when(secondPage.getUrl())
                .thenReturn(
                        "https://example.com/products");

        List<CrawledPage> pages = Arrays.asList(
                firstPage,
                secondPage);

        when(websiteCrawlerService.crawl())
                .thenReturn(pages);

        servlet.doPost(
                request,
                response);

        verify(websiteCrawlerService)
                .crawl();

        verify(solrService)
                .indexExternalPage(firstPage);

        verify(solrService)
                .indexExternalPage(secondPage);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_OK);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"success\""));

        assertTrue(
                body.contains(
                        "\"indexingTarget\":\"https://example.com\""));

        assertTrue(
                body.contains(
                        "\"crawledPages\":2"));

        assertTrue(
                body.contains(
                        "\"indexedPages\":2"));

        assertTrue(
                body.contains(
                        "\"failedPages\":0"));
    }

    @Test
    void testDoPostHandlesEmptyCrawlResult()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        when(websiteCrawlerService.crawl())
                .thenReturn(
                        Collections.emptyList());

        servlet.doPost(
                request,
                response);

        verify(websiteCrawlerService)
                .crawl();

        verify(solrService, never())
                .indexExternalPage(any(CrawledPage.class));

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_OK);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"success\""));

        assertTrue(
                body.contains(
                        "\"crawledPages\":0"));

        assertTrue(
                body.contains(
                        "\"indexedPages\":0"));

        assertTrue(
                body.contains(
                        "\"failedPages\":0"));
    }

    // ---------------------------------------------------------------------
    // Partial indexing failure
    // ---------------------------------------------------------------------

    @Test
    void testDoPostContinuesWhenOnePageFailsToIndex()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        CrawledPage firstPage = mock(
                CrawledPage.class);

        CrawledPage secondPage = mock(
                CrawledPage.class);

        CrawledPage thirdPage = mock(
                CrawledPage.class);

        when(firstPage.getUrl())
                .thenReturn(
                        "https://example.com/");

        when(secondPage.getUrl())
                .thenReturn(
                        "https://example.com/products");

        when(thirdPage.getUrl())
                .thenReturn(
                        "https://example.com/services");

        List<CrawledPage> pages = Arrays.asList(
                firstPage,
                secondPage,
                thirdPage);

        when(websiteCrawlerService.crawl())
                .thenReturn(pages);

        doThrow(
                new SolrIndexException(
                        "Failed to index product page"))
                .when(solrService)
                .indexExternalPage(secondPage);

        servlet.doPost(
                request,
                response);

        verify(solrService)
                .indexExternalPage(firstPage);

        verify(solrService)
                .indexExternalPage(secondPage);

        verify(solrService)
                .indexExternalPage(thirdPage);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_OK);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"success\""));

        assertTrue(
                body.contains(
                        "\"crawledPages\":3"));

        assertTrue(
                body.contains(
                        "\"indexedPages\":2"));

        assertTrue(
                body.contains(
                        "\"failedPages\":1"));
    }

    @Test
    void testDoPostHandlesAllPagesFailingToIndex()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        CrawledPage firstPage = mock(
                CrawledPage.class);

        CrawledPage secondPage = mock(
                CrawledPage.class);

        when(firstPage.getUrl())
                .thenReturn(
                        "https://example.com/");

        when(secondPage.getUrl())
                .thenReturn(
                        "https://example.com/products");

        List<CrawledPage> pages = Arrays.asList(
                firstPage,
                secondPage);

        when(websiteCrawlerService.crawl())
                .thenReturn(pages);

        doThrow(
                new SolrIndexException(
                        "First indexing failure"))
                .when(solrService)
                .indexExternalPage(firstPage);

        doThrow(
                new SolrIndexException(
                        "Second indexing failure"))
                .when(solrService)
                .indexExternalPage(secondPage);

        servlet.doPost(
                request,
                response);

        verify(solrService)
                .indexExternalPage(firstPage);

        verify(solrService)
                .indexExternalPage(secondPage);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_OK);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"crawledPages\":2"));

        assertTrue(
                body.contains(
                        "\"indexedPages\":0"));

        assertTrue(
                body.contains(
                        "\"failedPages\":2"));
    }

    // ---------------------------------------------------------------------
    // Website crawler failure
    // ---------------------------------------------------------------------

    @Test
    void testDoPostReturnsInternalServerErrorWhenCrawlerFails()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        when(websiteCrawlerService.crawl())
                .thenThrow(
                        new WebsiteCrawlerException(
                                "External website is unavailable"));

        servlet.doPost(
                request,
                response);

        verify(websiteCrawlerService)
                .crawl();

        verify(solrService, never())
                .indexExternalPage(any(CrawledPage.class));

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"error\""));

        assertTrue(
                body.contains(
                        "External website crawling failed"));

        assertTrue(
                body.contains(
                        "External website is unavailable"));
    }

    // ---------------------------------------------------------------------
    // Unexpected exception
    // ---------------------------------------------------------------------

    @Test
    void testDoPostReturnsInternalServerErrorForUnexpectedException()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        when(websiteCrawlerService.crawl())
                .thenThrow(
                        new RuntimeException(
                                "Unexpected failure"));

        servlet.doPost(
                request,
                response);

        verify(websiteCrawlerService)
                .crawl();

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"error\""));

        assertTrue(
                body.contains(
                        "Unexpected error during external Solr indexing"));

        assertTrue(
                body.contains(
                        "Unexpected failure"));
    }

    @Test
    void testDoPostHandlesUnexpectedExceptionDuringIndexing()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        CrawledPage page = mock(
                CrawledPage.class);

        when(page.getUrl())
                .thenReturn(
                        "https://example.com/products");

        when(websiteCrawlerService.crawl())
                .thenReturn(
                        Collections.singletonList(page));

        doThrow(
                new RuntimeException(
                        "Unexpected Solr failure"))
                .when(solrService)
                .indexExternalPage(page);

        servlet.doPost(
                request,
                response);

        verify(solrService)
                .indexExternalPage(page);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"status\":\"error\""));

        assertTrue(
                body.contains(
                        "Unexpected error during external Solr indexing"));

        assertTrue(
                body.contains(
                        "Unexpected Solr failure"));
    }

    // ---------------------------------------------------------------------
    // JSON escaping
    // ---------------------------------------------------------------------

    @Test
    void testDoPostEscapesIndexingTargetInErrorResponse()
            throws Exception {

        String target = "https://example.com/test\"page";

        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        when(solrService.getIndexingTarget())
                .thenReturn(target);

        servlet.doPost(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_BAD_REQUEST);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "https://example.com/test\\\"page"));
    }

    @Test
    void testDoPostEscapesCrawlerExceptionMessage()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        when(websiteCrawlerService.crawl())
                .thenThrow(
                        new WebsiteCrawlerException(
                                "Failed \"to\" crawl\nwebsite"));

        servlet.doPost(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "Failed \\\"to\\\" crawl\\nwebsite"));
    }

    @Test
    void testDoPostEscapesUnexpectedExceptionMessage()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(true);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        when(websiteCrawlerService.crawl())
                .thenThrow(
                        new RuntimeException(
                                "Unexpected \"failure\"\n"));

        servlet.doPost(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "Unexpected \\\"failure\\\"\\n"));
    }

    // ---------------------------------------------------------------------
    // Response configuration
    // ---------------------------------------------------------------------

    @Test
    void testDoPostSetsJsonContentTypeAndEncoding()
            throws Exception {

        when(solrService.isExternalIndexingTarget())
                .thenReturn(false);

        when(solrService.getIndexingTarget())
                .thenReturn(
                        "https://example.com");

        servlet.doPost(
                request,
                response);

        verify(response)
                .setContentType(
                        "application/json");

        verify(response)
                .setCharacterEncoding(
                        "UTF-8");
    }

    // ---------------------------------------------------------------------
    // Reflection helper
    // ---------------------------------------------------------------------

    private void injectDependency(
            String fieldName,
            Object dependency)
            throws Exception {

        java.lang.reflect.Field field = ExternalSolrIndexServlet.class
                .getDeclaredField(
                        fieldName);

        field.setAccessible(true);

        field.set(
                servlet,
                dependency);
    }
}