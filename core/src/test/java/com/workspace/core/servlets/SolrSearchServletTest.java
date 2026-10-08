package com.workspace.core.servlets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.workspace.core.models.SolrSearchResponse;
import com.workspace.core.models.SolrSearchResult;
import com.workspace.core.services.SolrService;
import com.workspace.core.services.impl.SolrServiceImpl.SolrIndexException;

class SolrSearchServletTest {

    private SolrSearchServlet servlet;

    private SolrService solrService;

    private SlingHttpServletRequest request;

    private SlingHttpServletResponse response;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {

        servlet = new SolrSearchServlet();

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
                "solrService",
                solrService);
    }

    // ---------------------------------------------------------------------
    // Query validation
    // ---------------------------------------------------------------------

    @Test
    void testDoGetReturnsBadRequestWhenQueryIsNull()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn(null);

        servlet.doGet(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_BAD_REQUEST);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"error\":\"Missing required query parameter: q\""));

        verifyNoInteractions(
                solrService);
    }

    @Test
    void testDoGetReturnsBadRequestWhenQueryIsBlank()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("   ");

        servlet.doGet(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_BAD_REQUEST);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"error\":\"Missing required query parameter: q\""));

        verifyNoInteractions(
                solrService);
    }

    @Test
    void testDoGetReturnsBadRequestWhenQueryIsEmpty()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("");

        servlet.doGet(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_BAD_REQUEST);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"error\":\"Missing required query parameter: q\""));

        verifyNoInteractions(
                solrService);
    }

    // ---------------------------------------------------------------------
    // Successful search
    // ---------------------------------------------------------------------

    @Test
    void testDoGetSuccessfullyReturnsSearchResponse()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        SolrSearchResponse searchResponse = new SolrSearchResponse(
                "AEM",
                1,
                java.util.Collections.emptyList());

        when(solrService.searchPages("AEM"))
                .thenReturn(searchResponse);

        servlet.doGet(
                request,
                response);

        verify(solrService)
                .searchPages("AEM");

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_OK);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"query\":\"AEM\""));

        assertTrue(
                body.contains(
                        "\"totalResults\":1"));

        assertTrue(
                body.contains(
                        "\"results\":[]"));
    }

    @Test
    void testDoGetPassesQueryToSolrService()
            throws Exception {

        String query = "Java development";

        when(request.getParameter("q"))
                .thenReturn(query);

        SolrSearchResponse searchResponse = new SolrSearchResponse(
                query,
                0,
                java.util.Collections.emptyList());

        when(solrService.searchPages(query))
                .thenReturn(searchResponse);

        servlet.doGet(
                request,
                response);

        verify(solrService)
                .searchPages(query);
    }

    @Test
    void testDoGetPreservesQueryWhitespace()
            throws Exception {

        String query = "  AEM search  ";

        when(request.getParameter("q"))
                .thenReturn(query);

        SolrSearchResponse searchResponse = new SolrSearchResponse(
                query,
                0,
                java.util.Collections.emptyList());

        when(solrService.searchPages(query))
                .thenReturn(searchResponse);

        servlet.doGet(
                request,
                response);

        verify(solrService)
                .searchPages(query);
    }

    @Test
    void testDoGetReturnsSearchResults()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("Products");

        SolrSearchResponse searchResponse = new SolrSearchResponse(
                "Products",
                2,
                java.util.Collections.emptyList());

        when(solrService.searchPages("Products"))
                .thenReturn(searchResponse);

        servlet.doGet(
                request,
                response);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"query\":\"Products\""));

        assertTrue(
                body.contains(
                        "\"totalResults\":2"));
    }

    // ---------------------------------------------------------------------
    // Solr service failure
    // ---------------------------------------------------------------------

    @Test
    void testDoGetReturnsInternalServerErrorWhenSolrFails()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        when(solrService.searchPages("AEM"))
                .thenThrow(
                        new SolrIndexException(
                                "Solr server unavailable"));

        servlet.doGet(
                request,
                response);

        verify(solrService)
                .searchPages("AEM");

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"error\":\"Solr server unavailable\""));
    }

    @Test
    void testDoGetEscapesQuotesInSolrException()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        when(solrService.searchPages("AEM"))
                .thenThrow(
                        new SolrIndexException(
                                "Solr \"server\" failed"));

        servlet.doGet(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "Solr \\\"server\\\" failed"));
    }

    @Test
    void testDoGetEscapesBackslashInSolrException()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        when(solrService.searchPages("AEM"))
                .thenThrow(
                        new SolrIndexException(
                                "Solr\\server failed"));

        servlet.doGet(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "Solr\\\\server failed"));
    }

    @Test
    void testDoGetHandlesNullSolrExceptionMessage()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        when(solrService.searchPages("AEM"))
                .thenThrow(
                        new SolrIndexException(
                                (String) null));

        servlet.doGet(
                request,
                response);

        verify(response)
                .setStatus(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"error\":\"\""));
    }

    // ---------------------------------------------------------------------
    // Response configuration
    // ---------------------------------------------------------------------

    @Test
    void testDoGetSetsJsonContentTypeOnSuccess()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        SolrSearchResponse searchResponse = new SolrSearchResponse(
                "AEM",
                0,
                java.util.Collections.emptyList());

        when(solrService.searchPages("AEM"))
                .thenReturn(searchResponse);

        servlet.doGet(
                request,
                response);

        verify(response)
                .setContentType(
                        "application/json");

        verify(response)
                .setCharacterEncoding(
                        "UTF-8");
    }

    @Test
    void testDoGetSetsJsonContentTypeOnValidationError()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn(null);

        servlet.doGet(
                request,
                response);

        verify(response)
                .setContentType(
                        "application/json");

        verify(response)
                .setCharacterEncoding(
                        "UTF-8");
    }

    @Test
    void testDoGetSetsJsonContentTypeOnSolrError()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        when(solrService.searchPages("AEM"))
                .thenThrow(
                        new SolrIndexException(
                                "Solr failure"));

        servlet.doGet(
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
    // JSON serialization
    // ---------------------------------------------------------------------

    @Test
    void testDoGetSerializesMultipleResults()
            throws Exception {

        when(request.getParameter("q"))
                .thenReturn("AEM");

        SolrSearchResult firstResult = new SolrSearchResult(
                "/content/site/aem",
                "AEM",
                "AEM content",
                "AEM",
                "Products",
                "https://example.com/aem");

        SolrSearchResult secondResult = new SolrSearchResult(
                "/content/site/java",
                "Java",
                "Java content",
                "Java",
                "Development",
                "https://example.com/java");

        SolrSearchResponse searchResponse = new SolrSearchResponse(
                "AEM",
                5,
                java.util.Arrays.asList(
                        firstResult,
                        secondResult));

        when(solrService.searchPages("AEM"))
                .thenReturn(searchResponse);

        servlet.doGet(
                request,
                response);

        String body = responseWriter.toString();

        assertTrue(
                body.contains(
                        "\"totalResults\":5"));

        assertTrue(
                body.contains(
                        "/content/site/aem"));

        assertTrue(
                body.contains(
                        "/content/site/java"));
    }

    // ---------------------------------------------------------------------
    // Reflection helper
    // ---------------------------------------------------------------------

    private void injectDependency(
            String fieldName,
            Object dependency)
            throws Exception {

        java.lang.reflect.Field field = SolrSearchServlet.class
                .getDeclaredField(
                        fieldName);

        field.setAccessible(true);

        field.set(
                servlet,
                dependency);
    }
}