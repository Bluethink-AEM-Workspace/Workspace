package com.workspace.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SolrSearchResponseTest {

    @Test
    void shouldReturnValuesProvidedToConstructor() {

        String query = "aem";
        long totalResults = 2;

        SolrSearchResult firstResult = new SolrSearchResult(
                "/content/workspace/language-masters/en/products/aem",
                "/content/workspace/language-masters/en/products/aem",
                "",
                "AEM",
                "Adobe Experience Manager content",
                "aem");

        SolrSearchResult secondResult = new SolrSearchResult(
                "https://www.bluethinkinc.com/about",
                "https://www.bluethinkinc.com/about",
                "https://www.bluethinkinc.com/about",
                "About Bluethink",
                "About Bluethink content",
                "external");

        List<SolrSearchResult> results = Arrays.asList(firstResult, secondResult);

        SolrSearchResponse response = new SolrSearchResponse(
                query,
                totalResults,
                results);

        assertEquals(
                query,
                response.getQuery());

        assertEquals(
                totalResults,
                response.getTotalResults());

        assertSame(
                results,
                response.getResults());
    }

    @Test
    void shouldAllowEmptyResults() {

        String query = "unknown";
        long totalResults = 0;

        List<SolrSearchResult> results = List.of();

        SolrSearchResponse response = new SolrSearchResponse(
                query,
                totalResults,
                results);

        assertEquals(
                query,
                response.getQuery());

        assertEquals(
                totalResults,
                response.getTotalResults());

        assertSame(
                results,
                response.getResults());
    }

    @Test
    void shouldAllowNullValues() {

        SolrSearchResponse response = new SolrSearchResponse(
                null,
                0,
                null);

        assertEquals(
                null,
                response.getQuery());

        assertEquals(
                0,
                response.getTotalResults());

        assertEquals(
                null,
                response.getResults());
    }
}