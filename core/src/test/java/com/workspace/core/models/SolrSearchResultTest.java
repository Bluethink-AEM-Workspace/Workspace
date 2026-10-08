package com.workspace.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SolrSearchResultTest {

    @Test
    void shouldReturnValuesProvidedToConstructor() {

        String id = "/content/workspace/language-masters/en/products/aem";
        String pagePath = "/content/workspace/language-masters/en/products/aem";
        String pageUrl = "";
        String pageTitle = "AEM";
        String pageContent = "Adobe Experience Manager content";
        String source = "aem";

        SolrSearchResult result = new SolrSearchResult(
                id,
                pagePath,
                pageUrl,
                pageTitle,
                pageContent,
                source);

        assertEquals(
                id,
                result.getId());

        assertEquals(
                pagePath,
                result.getPagePath());

        assertEquals(
                pageUrl,
                result.getPageUrl());

        assertEquals(
                pageTitle,
                result.getPageTitle());

        assertEquals(
                pageContent,
                result.getPageContent());

        assertEquals(
                source,
                result.getSource());
    }

    @Test
    void shouldSupportExternalSearchResult() {

        String id = "https://www.bluethinkinc.com/about";

        String pagePath = "https://www.bluethinkinc.com/about";

        String pageUrl = "https://www.bluethinkinc.com/about";

        String pageTitle = "About Bluethink";

        String pageContent = "About Bluethink content";

        String source = "external";

        SolrSearchResult result = new SolrSearchResult(
                id,
                pagePath,
                pageUrl,
                pageTitle,
                pageContent,
                source);

        assertEquals(
                id,
                result.getId());

        assertEquals(
                pagePath,
                result.getPagePath());

        assertEquals(
                pageUrl,
                result.getPageUrl());

        assertEquals(
                pageTitle,
                result.getPageTitle());

        assertEquals(
                pageContent,
                result.getPageContent());

        assertEquals(
                source,
                result.getSource());
    }

    @Test
    void shouldAllowNullValues() {

        SolrSearchResult result = new SolrSearchResult(
                null,
                null,
                null,
                null,
                null,
                null);

        assertEquals(
                null,
                result.getId());

        assertEquals(
                null,
                result.getPagePath());

        assertEquals(
                null,
                result.getPageUrl());

        assertEquals(
                null,
                result.getPageTitle());

        assertEquals(
                null,
                result.getPageContent());

        assertEquals(
                null,
                result.getSource());
    }
}